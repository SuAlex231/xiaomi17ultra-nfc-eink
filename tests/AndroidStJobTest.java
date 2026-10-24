package org.nfcresearch.qpolllab;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;

/** On-device validation only. Never starts NFC or calls PanelSession. */
public final class AndroidStJobTest {
    private static void write(File file,byte[] bytes)throws Exception{Files.write(file.toPath(),bytes);}
    private static void check(File file,JSONObject job,boolean valid)throws Exception{
        write(file,job.toString().getBytes("UTF-8"));
        try{StReaderProcess.validateRefreshJob(file);if(!valid)throw new AssertionError("invalid refresh accepted");}
        catch(IllegalArgumentException|IOException|org.json.JSONException expected){if(valid)throw expected;}
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);if(!dir.mkdir())throw new IOException("new test directory required");
        File file=new File(dir,"job.json"),image=new File(dir,"image.bin"),outside=new File(dir.getParentFile(),dir.getName()+"-outside.bin");
        try{
            byte[] data=new byte[PanelProtocol.IMAGE_BYTES];write(image,data);write(outside,data);
            JSONObject job=new JSONObject();job.put("confirmed",true);job.put("panelType","A0");job.put("powerMs",2000);job.put("pollMs",100);
            job.put("imageFile",image.toString());String hash=PanelProtocol.toHex(MessageDigest.getInstance("SHA-256").digest(data));job.put("sha256",hash);
            check(file,job,true);job.put("panelType","A1");check(file,job,true);
            job.put("confirmed",false);check(file,job,false);job.put("confirmed",true);
            job.put("panelType","");check(file,job,false);job.put("panelType","A2");check(file,job,false);job.put("panelType","A1");
            job.put("powerMs",1);check(file,job,false);job.put("powerMs",2000);
            job.put("pollMs",501);check(file,job,false);job.put("pollMs",100);
            job.put("sha256","00");check(file,job,false);job.put("sha256",hash);
            job.put("imageFile",outside.toString());check(file,job,false);job.put("imageFile",image.toString());
            write(image,new byte[10]);check(file,job,false);
            System.out.println("PASS ST refresh job gates: A0/A1, confirmation, unknown type, timing, hash, path, size; NO NFC operations");
        }finally{file.delete();image.delete();outside.delete();dir.delete();}
    }
}
