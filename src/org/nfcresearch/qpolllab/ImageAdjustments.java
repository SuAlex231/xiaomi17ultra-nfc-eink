package org.nfcresearch.qpolllab;

/** Pixel edits run on a snapshot, so changing sliders never accumulates rounding errors. */
public final class ImageAdjustments {
    public static final class Result {
        public final int width,height;
        public final int[] pixels;
        Result(int width,int height,int[] pixels){this.width=width;this.height=height;this.pixels=pixels;}
    }
    public static Result apply(int[] source,int width,int height,int turns,boolean mirrorX,boolean mirrorY,int brightness,int contrast){
        if(width<1||height<1||(long)width*height!=source.length)throw new IllegalArgumentException("图片尺寸错误");
        if(brightness < -100||brightness>100||contrast < -100||contrast>100)throw new IllegalArgumentException("调色参数超出范围");
        turns=((turns%4)+4)%4;
        int w=turns%2==0?width:height,h=turns%2==0?height:width;
        int[] result=new int[source.length],lookup=new int[256];
        double factor=Math.pow(2,contrast/50.0),offset=brightness*2.55;
        for(int i=0;i<256;i++)lookup[i]=Math.max(0,Math.min(255,(int)Math.round((i-127.5)*factor+127.5+offset)));
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int nx=x,ny=y;
            switch(turns){case 1:nx=height-1-y;ny=x;break;case 2:nx=width-1-x;ny=height-1-y;break;case 3:nx=y;ny=width-1-x;break;default:break;}
            if(mirrorX)nx=w-1-nx;if(mirrorY)ny=h-1-ny;
            int color=source[y*width+x];
            result[ny*w+nx]=(color&0xff000000)|(lookup[(color>>>16)&255]<<16)|(lookup[(color>>>8)&255]<<8)|lookup[color&255];
        }
        return new Result(w,h,result);
    }
}
