package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All changes remain local to this dialog until Apply; no NFC operations. */
public final class ImageEditDialog extends Dialog {
    public interface Apply {void apply(Bitmap image,boolean cropped);}
    private final Handler main=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Bitmap source;
    private final int[] sourcePixels;
    private final ZoomPreview preview;
    private final CropView cropView;
    private final CheckBox crop;
    private final SeekBar brightness,contrast;
    private final TextView brightnessLabel,contrastLabel,status;
    private final Button apply,compare,mirrorX,mirrorY;
    private Bitmap edited;
    private int turns,cropGeneration,appliedCropGeneration=-1;
    private boolean flipX,flipY,showOriginal,resetting;
    private volatile int revision;
    private volatile boolean closed;
    private Runnable pending;

    public ImageEditDialog(Activity activity,Bitmap source,boolean startCrop,Apply onApply,Runnable onClose){
        super(activity,android.R.style.Theme_Material_Light_NoActionBar);
        this.source=source;edited=source;
        sourcePixels=new int[source.getWidth()*source.getHeight()];source.getPixels(sourcePixels,0,source.getWidth(),0,0,source.getWidth(),source.getHeight());
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(10));root.setBackgroundColor(0xfff4f7f5);
        root.addView(label("编辑图片",20));
        status=label("编辑后再进行四色转换；原相册照片不会被修改。",12);root.addView(status);
        FrameLayout viewport=new FrameLayout(activity);
        preview=new ZoomPreview(activity,source);viewport.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        cropView=new CropView(activity,source);viewport.addView(cropView,new FrameLayout.LayoutParams(-1,-1));
        root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout viewTools=new LinearLayout(activity);
        compare=button("对照编辑前");compare.setOnClickListener(v->{showOriginal=!showOriginal;updateImage();preview.fit();});viewTools.addView(compare,weighted());
        Button reset=button("重置本次编辑");viewTools.addView(reset,weighted());root.addView(viewTools);
        ScrollView scroll=new ScrollView(activity);LinearLayout tools=new LinearLayout(activity);tools.setOrientation(LinearLayout.VERTICAL);scroll.addView(tools);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(260)));
        LinearLayout geometry=new LinearLayout(activity);
        Button rotate=button("旋转 90°");rotate.setOnClickListener(v->{turns=(turns+1)%4;geometryChanged();});geometry.addView(rotate,weighted());
        mirrorX=button("水平镜像");mirrorX.setOnClickListener(v->{flipX=!flipX;geometryChanged();});geometry.addView(mirrorX,weighted());
        mirrorY=button("垂直镜像");mirrorY.setOnClickListener(v->{flipY=!flipY;geometryChanged();});geometry.addView(mirrorY,weighted());tools.addView(geometry);
        brightnessLabel=label("亮度：0",13);tools.addView(brightnessLabel);brightness=new SeekBar(activity);brightness.setMax(200);brightness.setProgress(100);brightness.setContentDescription("亮度");tools.addView(brightness);
        contrastLabel=label("对比度：0",13);tools.addView(contrastLabel);contrast=new SeekBar(activity);contrast.setMax(200);contrast.setProgress(100);contrast.setContentDescription("对比度");tools.addView(contrast);
        crop=new CheckBox(activity);crop.setText("裁剪到墨水屏比例 · 552×768");crop.setChecked(startCrop);tools.addView(crop);
        tools.addView(label("裁剪时单指拖动、双指缩放，白框内保留；旋转或镜像后重新取景。",12));
        LinearLayout actions=new LinearLayout(activity);Button cancel=button("取消");cancel.setOnClickListener(v->dismiss());actions.addView(cancel,weighted());
        apply=button("应用并预览四色");apply.setTextColor(0xffffffff);apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff176b61));
        apply.setOnClickListener(v->{try{Bitmap result=crop.isChecked()?cropView.result():edited;boolean hasCrop=crop.isChecked();dismiss();new Handler(getContext().getMainLooper()).post(()->onApply.apply(result,hasCrop));}catch(Exception e){status.setText("编辑失败："+e.getMessage());}});
        actions.addView(apply,new LinearLayout.LayoutParams(0,dp(48),2));root.addView(actions);setContentView(root);
        SeekBar.OnSeekBarChangeListener slider=new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){brightnessLabel.setText("亮度："+(brightness.getProgress()-100));contrastLabel.setText("对比度："+(contrast.getProgress()-100));if(!resetting)schedule();}
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
        };brightness.setOnSeekBarChangeListener(slider);contrast.setOnSeekBarChangeListener(slider);
        crop.setOnCheckedChangeListener((b,checked)->{showOriginal=false;updateImage();});
        reset.setOnClickListener(v->{resetting=true;turns=0;flipX=false;flipY=false;brightness.setProgress(100);contrast.setProgress(100);crop.setChecked(false);resetting=false;geometryChanged();});
        setOnDismissListener(d->{closed=true;revision++;main.removeCallbacksAndMessages(null);worker.shutdownNow();onClose.run();});
        updateImage();schedule();
    }
    @Override protected void onStart(){super.onStart();getWindow().setLayout(-1,-1);}
    private void geometryChanged(){cropGeneration++;schedule();}
    private void schedule(){
        if(closed)return;
        showOriginal=false;updateImage();mirrorX.setText(flipX?"水平镜像 ✓":"水平镜像");mirrorY.setText(flipY?"垂直镜像 ✓":"垂直镜像");
        final int token=++revision,angle=turns,generation=cropGeneration,light=brightness.getProgress()-100,contrastValue=contrast.getProgress()-100;final boolean x=flipX,y=flipY;
        apply.setEnabled(false);status.setText("正在更新编辑预览…");if(pending!=null)main.removeCallbacks(pending);
        pending=()->worker.execute(()->{
            if(closed||token!=revision)return;
            try{
                ImageAdjustments.Result result=ImageAdjustments.apply(sourcePixels,source.getWidth(),source.getHeight(),angle,x,y,light,contrastValue);
                if(closed||token!=revision)return;
                Bitmap bitmap=Bitmap.createBitmap(result.pixels,result.width,result.height,Bitmap.Config.ARGB_8888);
                main.post(()->{if(closed||token!=revision)return;boolean geometry=appliedCropGeneration!=generation;edited=bitmap;cropView.setImage(bitmap,geometry);appliedCropGeneration=generation;updateImage();if(geometry)preview.fit();status.setText("旋转 "+(angle*90)+"° · "+result.width+"×"+result.height+" · 仅应用后更新发送预览");apply.setEnabled(true);});
            }catch(Throwable e){main.post(()->{if(!closed&&token==revision)status.setText("编辑失败："+e.getMessage());});}
        });main.postDelayed(pending,100);
    }
    private void updateImage(){preview.setImage(showOriginal?source:edited);preview.setVisibility(!showOriginal&&crop.isChecked()?View.GONE:View.VISIBLE);cropView.setVisibility(!showOriginal&&crop.isChecked()?View.VISIBLE:View.GONE);compare.setText(showOriginal?"返回编辑效果":"对照编辑前");}
    private int dp(int value){return (int)(value*getContext().getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size){TextView view=new TextView(getContext());view.setText(text);view.setTextSize(size);view.setTextColor(0xff263e35);view.setPadding(0,dp(3),0,dp(3));return view;}
    private Button button(String text){Button view=new Button(getContext());view.setText(text);view.setTextSize(13);view.setAllCaps(false);return view;}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,dp(48),1);}
}
