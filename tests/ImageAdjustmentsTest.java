package org.nfcresearch.qpolllab;

import java.util.Arrays;

public final class ImageAdjustmentsTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static ImageAdjustments.Result edit(int[] pixels,int turns,boolean x,boolean y,int brightness,int contrast){return ImageAdjustments.apply(pixels,3,2,turns,x,y,brightness,contrast);}
    public static void main(String[] args){
        int[] input={0xff010101,0xff020202,0xff030303,0xff040404,0xff050505,0xff060606},copy=input.clone();
        check(Arrays.equals(edit(input,0,false,false,0,0).pixels,input),"neutral edit must be exact");
        ImageAdjustments.Result rotated=edit(input,1,false,false,0,0);
        check(rotated.width==2&&rotated.height==3,"quarter turn dimensions");
        check(Arrays.equals(rotated.pixels,new int[]{input[3],input[0],input[4],input[1],input[5],input[2]}),"clockwise rotation");
        check(Arrays.equals(edit(input,4,false,false,0,0).pixels,input),"full turn");
        check(Arrays.equals(edit(input,0,true,false,0,0).pixels,new int[]{input[2],input[1],input[0],input[5],input[4],input[3]}),"horizontal mirror");
        check(Arrays.equals(edit(input,0,false,true,0,0).pixels,new int[]{input[3],input[4],input[5],input[0],input[1],input[2]}),"vertical mirror");
        check(Arrays.equals(edit(input,2,true,true,0,0).pixels,input),"rotation and both mirrors cancel");
        int[] samples={0xff000000,0xff404040,0xff808080,0xffc0c0c0,0xffffffff,0x804080c0};
        int[] lighter=edit(samples,0,false,false,20,0).pixels,darker=edit(samples,0,false,false,-20,0).pixels;
        for(int i=0;i<samples.length;i++){check((lighter[i]&255)>=(samples[i]&255),"brightness monotonic up");check((darker[i]&255)<=(samples[i]&255),"brightness monotonic down");check((lighter[i]>>>24)==(samples[i]>>>24),"alpha retained");}
        int[] strong=edit(samples,0,false,false,0,50).pixels,soft=edit(samples,0,false,false,0,-50).pixels;
        check((strong[1]&255)<64&&(strong[3]&255)>192,"contrast expands range");
        check((soft[1]&255)>64&&(soft[3]&255)<192,"contrast compresses range");
        check((edit(samples,0,false,false,100,100).pixels[4]&0xffffff)==0xffffff,"white clamps");
        check((edit(samples,0,false,false,-100,100).pixels[0]&0xffffff)==0,"black clamps");
        check(Arrays.equals(input,copy),"editing must not mutate source");
        System.out.println("PASS image edits: rotation, mirrors, brightness, contrast, clipping, alpha and immutable source");
    }
}
