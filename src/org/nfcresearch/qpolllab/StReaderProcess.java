package org.nfcresearch.qpolllab;

import android.system.Os;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Root retains ST configuration; Shell owns ReaderMode and the ISO-DEP session. */
public final class StReaderProcess {
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    public static void validateRefreshJob(File file)throws Exception {
        JSONObject settings=new JSONObject(new String(read(file,8192),"UTF-8"));
        if(!settings.optBoolean("confirmed",false))throw new IllegalArgumentException("ST 刷图必须先确认预览");
        PanelType.parse(settings.getString("panelType"));
        PanelProtocol.d4(settings.getInt("powerMs"));
        int poll=settings.getInt("pollMs");if(poll<0||poll>500)throw new IllegalArgumentException("忙查询间隔错误");
        File source=new File(settings.getString("imageFile")).getCanonicalFile();
        if(!source.getParentFile().equals(file.getCanonicalFile().getParentFile()))throw new IOException("图像不在会话目录");
        byte[] image=read(source,PanelProtocol.IMAGE_BYTES);
        if(image.length!=PanelProtocol.IMAGE_BYTES||!PanelProtocol.toHex(MessageDigest.getInstance("SHA-256").digest(image)).equalsIgnoreCase(settings.getString("sha256")))
            throw new IOException("ST 预览快照校验不一致");
    }
    public static void run(String mode,int seconds,File originalStop,File originalJob) throws Exception {
        if(!mode.matches("qtag|standard|identity|refresh"))throw new IllegalArgumentException("ST 模式错误");
        if(mode.equals("refresh"))validateRefreshJob(originalJob);
        File dir=new File("/data/local/tmp/qp-st-reader-"+android.os.Process.myPid()+"-"+System.nanoTime());
        if(!dir.mkdir())throw new IOException("无法创建 ST Shell 会话目录");
        Os.chmod(dir.toString(),0700);Os.chown(dir.toString(),2000,2000);
        File job=new File(dir,"job.json"),image=new File(dir,"image.bin"),stop=new File(dir,"stop");
        Process child=null;Thread monitor=null;
        try {
            JSONObject settings=new JSONObject("{}");
            if(mode.equals("refresh")){
                settings=new JSONObject(new String(read(originalJob,8192),"UTF-8"));
                write(image,read(new File(settings.getString("imageFile")),PanelProtocol.IMAGE_BYTES));
                settings.put("imageFile",image.toString());
            }
            write(job,settings.toString().getBytes("UTF-8"));
            if(originalStop.exists())return;
            String apk=System.getenv("CLASSPATH");
            if(apk==null||!new File(apk).isFile())throw new IOException("缺少 ST APK 路径");
            String command="QP_ST_READER=1 CLASSPATH="+quote(apk)+" /system/bin/app_process /system/bin org.nfcresearch.qpolllab.QPollWorker "
                    +mode+" "+seconds+" "+quote(stop.toString())+" "+quote(new File(dir,"reader.lock").toString())
                    +(mode.equals("identity")||mode.equals("refresh")?" "+quote(job.toString()):"");
            child=new ProcessBuilder("/system/bin/su","2000","-c",command).redirectErrorStream(true).start();
            final Process running=child;
            monitor=new Thread(()->{
                long end=android.os.SystemClock.elapsedRealtime()+310000;
                try{
                    while(running.isAlive()){
                        if(originalStop.exists()||android.os.SystemClock.elapsedRealtime()>end){
                            stop.createNewFile();
                            if(!running.waitFor(10,TimeUnit.SECONDS))running.destroy();return;
                        }
                        Thread.sleep(100);
                    }
                }catch(InterruptedException ignored){}catch(Exception e){running.destroy();}
            },"st-reader-watchdog");monitor.setDaemon(true);monitor.start();
            try(BufferedReader input=new BufferedReader(new InputStreamReader(child.getInputStream(),"UTF-8"))){
                String line;while((line=input.readLine())!=null){System.out.println(line);System.out.flush();}
            }
            int status=child.waitFor();if(status!=0)throw new IOException("ST Shell 读卡进程退出码="+status);
        } finally {
            if(child!=null&&child.isAlive()){stop.createNewFile();if(!child.waitFor(10,TimeUnit.SECONDS))child.destroyForcibly();}
            if(monitor!=null)monitor.interrupt();
            job.delete();image.delete();stop.delete();new File(dir,"reader.lock").delete();dir.delete();
        }
    }
    private static byte[] read(File file,int max)throws Exception {
        if(Files.size(file.toPath())>max)throw new IOException("ST 会话文件过大");
        byte[] data=Files.readAllBytes(file.toPath());if(data.length>max)throw new IOException("ST 会话文件过大");return data;
    }
    private static void write(File file,byte[] data)throws Exception {
        try(FileOutputStream output=new FileOutputStream(file)){output.write(data);output.getFD().sync();}
        Os.chmod(file.toString(),0600);Os.chown(file.toString(),2000,2000);
    }
}
