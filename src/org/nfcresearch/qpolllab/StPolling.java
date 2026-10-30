package org.nfcresearch.qpolllab;

/** ST custom polling TLVs. Adapted from wiwiwei/ink-nfc-lab (PolyForm Noncommercial). */
public final class StPolling {
    public static byte[] wakeup() { return new byte[]{1, 0x2a, 2, 10, 0x7a}; }
    public static void validate(byte[] payload) {
        if (payload == null || payload.length == 0 || (payload[0] & 255) > 4)
            throw new IllegalArgumentException("无有效 ST 轮询配置");
        int offset = 1;
        for (int frame = 0; frame < (payload[0] & 255); frame++) {
            if (offset + 3 > payload.length) throw new IllegalArgumentException("ST 轮询 TLV 头截断");
            int length = payload[offset + 1] & 255;
            if (length < 2 || offset + length + 2 > payload.length)
                throw new IllegalArgumentException("ST 轮询 TLV 长度错误");
            offset += length + 2;
        }
        if (offset != payload.length) throw new IllegalArgumentException("ST 轮询尾部数据异常");
    }
    public static boolean knownDevice(String device) { return "nezha".equals(device); }
    public static boolean supportedDevice(String device, int sdk) {
        return knownDevice(device) && (sdk == 36 || sdk == 37);
    }
    /** HAL vendor 0x14 carries the original RF activation; byte 4 is its RF technology mode. */
    public static boolean customActivation(int gid,int oid,byte[] payload){
        return gid==15&&oid==1&&payload!=null&&payload.length>=5&&(payload[0]&255)==0x14&&(payload[4]&255)==0x78;
    }
    /** Native GET includes the CRC bytes which the OEM SET wrapper adds itself. */
    public static byte[] toHost(byte[] nativeFrames) {
        validate(nativeFrames);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(nativeFrames[0]);
        int offset=1;
        for(int frame=0;frame<(nativeFrames[0]&255);frame++){
            int length=nativeFrames[offset+1]&255;
            boolean crc=(nativeFrames[offset+2]&0x80)!=0;
            if(crc&&length<4)throw new IllegalArgumentException("ST CRC 帧长度错误");
            out.write(nativeFrames[offset]);out.write(length-(crc?2:0));
            out.write(nativeFrames,offset+2,length-(crc?2:0));offset+=length+2;
        }
        return out.toByteArray();
    }
}
