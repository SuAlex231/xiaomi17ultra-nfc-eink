package org.nfcresearch.qpolllab;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.system.Os;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.zip.*;

/** ST54L backend derived from wiwiwei/ink-nfc-lab RootProbe, PolyForm Noncommercial.
 * Only temporary custom polling is changed; calibration and display drivers are retained.
 */
public final class StController implements AutoCloseable {
    private static NfcAdapter preparedAdapter;
    private final NfcAdapter adapter;
    private final Object callback;
    private final Class<?> callbackClass;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LinkedBlockingQueue<byte[]> replies = new LinkedBlockingQueue<>();
    private final File snapshot = new File("/data/local/tmp/qp-st-polling.snapshot");
    private final File compatActive = new File("/data/nfc/qp-st-compat.active");
    private final PanelSession.Events events;
    private volatile int expected = -1;
    private volatile int expectedOid = 1;
    private boolean paused, registered;

    public static boolean configured() {
        try {
            String config = new String(Files.readAllBytes(new File("/odm/etc/libnfc-hal-st.conf").toPath()), "UTF-8");
            return StPolling.supportedDevice(Build.DEVICE, Build.VERSION.SDK_INT)
                    && config.contains("STNFC_FW_BIN_NAME=\"st54l_fw.bin\"");
        } catch (Exception e) { return false; }
    }

    public static void prepareFramework() throws Exception {
        Class<?> manager = Class.forName("android.nfc.NfcServiceManager");
        Class<?> initializer = Class.forName("android.nfc.NfcFrameworkInitializer");
        if (initializer.getMethod("getNfcServiceManager").invoke(null) == null)
            initializer.getMethod("setNfcServiceManager", manager).invoke(null, manager.getConstructor().newInstance());
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context context = (Context) threadClass.getDeclaredMethod("getSystemContext").invoke(thread);
        preparedAdapter = NfcAdapter.getDefaultAdapter(context);
    }
    public StController(PanelSession.Events events) throws Exception {
        this.events = events;
        if (!configured()) throw new IllegalStateException("ST 后端需要 nezha / Android 16–17 / ST54L 配置；不回退到 NXP 命令");
        if (android.os.Process.myUid() != 0) throw new SecurityException("小米 17 Ultra 的 ST54L 兼容后端需要 Root；Shell Shizuku 尚未验证");
        adapter = preparedAdapter;
        if (adapter == null || !adapter.isEnabled()) throw new IllegalStateException("请先开启 NFC");
        callbackClass = Class.forName("android.nfc.NfcAdapter$NfcVendorNciCallback");
        callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, (proxy, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "QPollStCallback";
            if (method.getName().equals("onVendorNciResponse") && (Integer) args[0] == 15 && (Integer) args[1] == expectedOid) {
                byte[] data = (byte[]) args[2];
                if (data != null && data.length > 0 && expected >= 0 && (expectedOid == 0x1e || (data[0] & 255) == expected))
                    replies.offer(data.clone());
            }
            return null;
        });
        try {
            NfcAdapter.class.getMethod("registerNfcVendorNciCallback", Executor.class, callbackClass).invoke(adapter, executor, callback);
            registered = true;
        } catch (Exception e) { executor.shutdownNow(); throw e; }
        events.emit("BACKEND", "小米 17 Ultra / ST54L / Root 7A；未使用 NXP QTag 命令");
    }

    private byte[] exchange(int subcommand, byte[] data) throws Exception {
        byte[] command = new byte[data.length + 1];
        command[0] = (byte) subcommand;
        System.arraycopy(data, 0, command, 1, data.length);
        replies.clear(); expectedOid=1; expected = subcommand;
        try {
            int status = (Integer) NfcAdapter.class.getMethod("sendVendorNciMessage", int.class, int.class, int.class, byte[].class)
                    .invoke(adapter, 1, 15, 1, command);
            if (status != 0) throw new IOException("ST 厂商接口返回 " + status);
            byte[] response = replies.poll(2, TimeUnit.SECONDS);
            if (response == null || response.length < 2 || response[1] != 0)
                throw new IOException("ST 响应未确认：" + (response == null ? "timeout" : PanelProtocol.toHex(response)));
            events.emit("ST_NCI", PanelProtocol.toHex(command) + " → " + PanelProtocol.toHex(response));
            return Arrays.copyOfRange(response, 2, response.length);
        } finally { expected = -1; }
    }
    private byte[] read() throws Exception {
        // ST's Android 16 HAL has no OEM GET subcommand 0x15. Use the official
        // native RF_GET_CUST_PASSIVE_POLL_FRAME OID 0x1E, not an invented subcommand.
        replies.clear(); expectedOid=0x1e; expected=0;
        try {
            int status=(Integer)NfcAdapter.class.getMethod("sendVendorNciMessage",int.class,int.class,int.class,byte[].class)
                    .invoke(adapter,1,15,0x1e,new byte[0]);
            if(status!=0)throw new IOException("ST 原生查询接口返回 "+status);
            byte[] response=replies.poll(2,TimeUnit.SECONDS);
            if(response==null||response.length<2||response[0]!=0)
                throw new IOException("ST 原生查询未确认："+(response==null?"timeout":PanelProtocol.toHex(response)));
            byte[] data=Arrays.copyOfRange(response,1,response.length);StPolling.validate(data);
            events.emit("ST_NCI","OID=1E → "+PanelProtocol.toHex(response));return data;
        } finally {expected=-1;}
    }
    private void write(byte[] data) throws Exception { exchange(0x13, StPolling.toHost(data)); }
    private void pause() throws Exception {
        if (paused) return;
        Object extension = NfcAdapter.class.getMethod("getNfcOemExtension").invoke(adapter);
        for (int attempt = 0; attempt < 8; attempt++) {
            int result = (Integer) extension.getClass().getMethod("pausePolling", long.class).invoke(extension, 30000L);
            if (result == 1) { paused = true; return; }
            if (result != 2) throw new IOException("暂停 ST 轮询返回 " + result);
            Thread.sleep(120);
        }
        throw new IOException("无法暂停 ST 轮询");
    }
    private void resume() throws Exception {
        if (paused) { NfcAdapter.class.getMethod("resumePolling").invoke(adapter); paused = false; }
    }
    private void recover() throws Exception {
        if (!snapshot.exists()) return;
        byte[] original = Files.readAllBytes(snapshot.toPath()); StPolling.validate(original);
        write(original);
        if (!Arrays.equals(original, read())) throw new IOException("ST 原始配置恢复读回不一致，保留快照");
        if (!snapshot.delete()) throw new IOException("ST 恢复完成但快照无法删除");
        events.emit("RESTORED", "ST 原始轮询配置已逐字节读回确认");
    }
    public void inspect() throws Exception {
        pause();
        try {
            recover();
            events.emit("ST_BASELINE", PanelProtocol.toHex(read()));
            events.emit("READY", "ST54L 厂商回调及轮询查询通过；尚未验证标签通信");
        } finally { resume(); }
    }
    public void preflight() throws Exception {
        pause();try {recover();StPolling.toHost(read());}finally {resume();}
    }
    public void enable() throws Exception {
        pause();
        try {
            recover();
            byte[] original = read();
            try (FileOutputStream output = new FileOutputStream(snapshot)) {
                Os.chmod(snapshot.getPath(), 0600); output.write(original); output.getFD().sync();
            }
            write(StPolling.wakeup());
            if (!Arrays.equals(StPolling.wakeup(), read())) throw new IOException("ST 7A 读回不一致");
            events.emit("CAPABILITY", "ST 7A 配置接受并读回一致；等待目标标签，尚未证明刷新成功");
        } finally { resume(); }
    }

    public void installCompatibility(String apkPath) throws Exception {
        File injector = new File("/data/local/tmp/qp-st-frida-inject");
        File script = new File("/data/local/tmp/qp-st-compat.js");
        extract(apkPath, "frida-inject", injector, 0700);
        extract(apkPath, "st-mifare-compat.js", script, 0600);
        try(FileOutputStream output=new FileOutputStream(compatActive)) {
            Os.chmod(compatActive.getPath(),0600);
            output.write(Long.toString(System.nanoTime()).getBytes("UTF-8"));output.getFD().sync();
        }
        Os.chown(compatActive.getPath(),1027,1027);
        command("/system/bin/restorecon",compatActive.getPath());
        String version = command(injector.toString(), "--version");
        if (!version.trim().equals("17.24.0")) throw new IOException("Frida 版本不匹配：" + version);
        String result = command(injector.toString(), "--name", "com.android.nfc", "--script", script.toString(), "--eternalize");
        if (!result.contains("INK_NFC_MIFARE_COMPAT_READY")) throw new IOException("ST 激活兼容未确认：" + result);
        events.emit("ST_COMPAT", "临时 MIFARE 重选拦截已就绪；330 秒后自动卸载，不限制 UID");
    }
    private static void extract(String apkPath, String name, File destination, int mode) throws Exception {
        try (ZipFile apk = new ZipFile(apkPath)) {
            ZipEntry entry = apk.getEntry("assets/" + name);
            if (entry == null) throw new IOException("缺少 ST 兼容资源：" + name);
            try (InputStream input = apk.getInputStream(entry); FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                output.getFD().sync();
            }
            Os.chmod(destination.getPath(), mode);
        }
    }
    private static String command(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) { if (output.size() < 65536) output.write(buffer, 0, n); }
            } catch (IOException ignored) { }
        }); reader.start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); reader.join(1000); throw new IOException("ST 兼容命令超时"); }
        reader.join(1000);
        String result = new String(output.toByteArray(), "UTF-8");
        if (process.exitValue() != 0) throw new IOException("ST 兼容命令失败：" + result);
        return result;
    }
    @Override public void close() throws Exception {
        compatActive.delete();
        Exception failure = null;
        try { if (snapshot.exists()) { pause(); recover(); } } catch (Exception e) { failure = e; }
        try { resume(); } catch (Exception e) { if (failure == null) failure = e; }
        try { if (registered) NfcAdapter.class.getMethod("unregisterNfcVendorNciCallback", callbackClass).invoke(adapter, callback); }
        catch (Exception e) { if (failure == null) failure = e; }
        executor.shutdownNow();
        if (failure != null) throw failure;
    }
}
