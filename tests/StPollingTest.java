package org.nfcresearch.qpolllab;
public final class StPollingTest {
    private static void check(boolean value) { if(!value)throw new AssertionError(); }
    private static void invalid(byte[] data) {
        try { StPolling.validate(data); throw new AssertionError("accepted malformed polling"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        check(PanelProtocol.toHex(StPolling.wakeup()).equals("012A020A7A"));
        StPolling.validate(StPolling.wakeup()); StPolling.validate(new byte[]{0});
        check(java.util.Arrays.equals(StPolling.toHost(StPolling.wakeup()),StPolling.wakeup()));
        check(java.util.Arrays.equals(StPolling.toHost(new byte[]{0}),new byte[]{0}));
        check(java.util.Arrays.equals(StPolling.toHost(new byte[]{1,0x29,5,(byte)0x8a,0x30,0x01,0x12,0x34}),
                new byte[]{1,0x29,3,(byte)0x8a,0x30,0x01}));
        try{StPolling.toHost(new byte[]{1,0x29,3,(byte)0x8a,0x30,0x01});throw new AssertionError();}catch(IllegalArgumentException expected){}
        StPolling.validate(new byte[]{2,0x2a,2,10,0x26,0x2a,2,10,0x52});
        invalid(null); invalid(new byte[0]); invalid(new byte[]{5}); invalid(new byte[]{1});
        invalid(new byte[]{1,0x2a,1,0}); invalid(new byte[]{1,0x2a,2,10}); invalid(new byte[]{0,1});
        check(StPolling.supportedDevice("nezha",36));
        check(StPolling.supportedDevice("nezha",37));
        check(!StPolling.supportedDevice("nezha",35)); check(!StPolling.supportedDevice("nezha",38));
        check(StPolling.knownDevice("nezha"));check(!StPolling.knownDevice(null));
        check(!StPolling.supportedDevice("pudding",36));check(!StPolling.supportedDevice("pudding",37)); check(!StPolling.supportedDevice(null,36));
        System.out.println("ST polling TLV and Xiaomi 17 Ultra profile tests passed");
    }
}
