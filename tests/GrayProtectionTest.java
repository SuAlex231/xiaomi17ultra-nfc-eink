package org.nfcresearch.qpolllab;

import java.util.Arrays;

public final class GrayProtectionTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        int w=552,h=768;int[] grayscale=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int value=x*255/(w-1);grayscale[y*w+x]=0xff000000|(value<<16)|(value<<8)|value;}
        int[] original=grayscale.clone();
        byte[] legacy=ImageRaster.quantize(grayscale,w,h,false,128,false,1);
        boolean legacyTint=false;for(byte code:legacy)legacyTint|=code>=2;
        check(legacyTint,"Regression fixture must expose grayscale red/yellow tint");
        for(boolean dither:new boolean[]{false,true})for(int block:new int[]{1,2,4,8,16}){
            byte[] codes=ImageRaster.quantize(grayscale,w,h,false,128,dither,block,true);
            for(byte code:codes)check(code==0||code==1,"Protected grayscale contains a chromatic code");
            byte[] packed=PanelType.A1.pack(codes);int[] preview=PanelType.A1.preview(packed);
            for(int i=0;i<codes.length;i++)check(preview[i]==ImageRaster.color(codes[i]),"A1 preview differs from exact packed pixels");
        }
        check(Arrays.equals(original,grayscale),"Source image was mutated");
        int[] palette={ImageRaster.color(0),ImageRaster.color(1),ImageRaster.color(2),ImageRaster.color(3)};
        check(Arrays.equals(ImageRaster.quantize(palette,4,1,false,128,false,1,true),new byte[]{0,1,2,3}),"Warm panel colours changed");
        check(ImageRaster.isNeutral(100,109,118),"Neutral threshold boundary");
        check(!ImageRaster.isNeutral(100,109,119),"Colour outside neutral threshold");
        check(Arrays.equals(ImageRaster.quantize(palette,4,1,true,128,true,1,true),ImageRaster.quantize(palette,4,1,true,128,true,1,false)),"Binary mode changed");
        check(Arrays.equals(legacy,ImageRaster.quantize(grayscale,w,h,false,128,false,1,false)),"Legacy toggle compatibility changed");
        System.out.println("Gray protection passed: legacy tint reproduced; ten full-frame modes stay black/white; exact A1 preview, warm colours, source immutability, legacy toggle");
    }
}
