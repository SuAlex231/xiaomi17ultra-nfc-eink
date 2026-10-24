package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public foreground ReaderMode: the app receives tags without normal intent dispatch. */
public final class ForegroundNfcSession {
    private final Activity activity;
    private final String mode;
    private final int seconds;
    private final File job,stop,done;
    private final PanelSession.Events events;
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final AtomicBoolean claimed=new AtomicBoolean(),finished=new AtomicBoolean();
    private final CountDownLatch ended=new CountDownLatch(1);
    private NfcAdapter adapter;
    private boolean enabled;
    public ForegroundNfcSession(Activity activity,String mode,int seconds,File job,File stop,File done,PanelSession.Events events){
        this.activity=activity;this.mode=mode;this.seconds=seconds;this.job=job;this.stop=stop;this.done=done;this.events=events;
    }
    public void start(){
        try{
            if(stop.exists()){finish();return;}
            adapter=NfcAdapter.getDefaultAdapter(activity);
            if(adapter==null||!adapter.isEnabled())throw new IOException("请先开启 NFC");
            Bundle extras=new Bundle();extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,5000);
            enabled=true;
            adapter.enableReaderMode(activity,this::tag,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK|NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,extras);
            events.emit("FOREGROUND_READER","前台应用 ReaderMode 已申请；UID="+android.os.Process.myUid()+"；跳过 NDEF / 系统标签应用分发");
            events.emit("START",mode+":"+seconds);
            main.postDelayed(()->{if(!finished.get()&&claimed.compareAndSet(false,true)){events.emit("RESULT","本轮超时，未收到标签；未发送屏幕指令");finish();}},seconds*1000L);
        }catch(Exception e){events.emit("ERROR","前台 NFC 启动失败："+e);finish();}
    }
    private void tag(Tag tag){
        if(stop.exists()||finished.get()||!claimed.compareAndSet(false,true))return;
        events.emit("TAG","UID="+PanelProtocol.toHex(tag.getId())+"；技术="+Arrays.toString(tag.getTechList()));
        new Thread(()->{
            try{
                if(mode.equals("identity")||mode.equals("refresh")){
                    PanelSession.run(tag,mode,job,stop,events);
                    if(mode.equals("identity"))events.emit("RESULT","D1 屏幕规格与原厂配置校验通过，未写图像");
                }else events.emit("RESULT","已收到标签；只显示元信息，未发送 APDU");
            }catch(InterruptedException e){events.emit("CANCELLED",e.getMessage());}
            catch(Exception e){events.emit("ERROR",e.toString());}
            finally{finish();}
        },"foreground-panel-session").start();
    }
    public void cancel(){
        try{stop.createNewFile();}catch(IOException e){events.emit("ERROR","停止标记失败："+e);}
        if(claimed.compareAndSet(false,true))finish();
    }
    private void finish(){
        if(!finished.compareAndSet(false,true))return;
        main.post(()->{
            try{if(enabled&&adapter!=null)adapter.disableReaderMode(activity);enabled=false;events.emit("READER_OFF","前台 ReaderMode 已释放，交还系统分发");}
            catch(Exception e){events.emit("CLEANUP_ERROR","释放前台 ReaderMode："+e);}
            finally{
                try{done.createNewFile();stop.createNewFile();}catch(IOException e){events.emit("CLEANUP_ERROR","前台结束标记失败："+e);}
                main.removeCallbacksAndMessages(null);ended.countDown();
            }
        });
    }
    public boolean awaitStopped(long milliseconds)throws InterruptedException{return ended.await(milliseconds,TimeUnit.MILLISECONDS);}
}
