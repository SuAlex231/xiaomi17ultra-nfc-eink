package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Screen-specific conversion controls; preview is decoded from the exact packed raster. */
public final class ColorConversionDialog extends Dialog {
    public interface Apply {void apply(boolean binary,boolean dither,int block,int threshold,boolean protectGrays);}
    private final Handler main=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final int[] blockSizes={1,2,4,8,16};
    private final Bitmap source,original;
    private final int rotation;
    private final boolean cover,cropped;
    private final String panel;
    private final ZoomPreview preview;
    private final Spinner mode,blocks;
    private final SeekBar threshold;
    private final CheckBox protect;
    private final TextView status,thresholdLabel;
    private final Button apply,compare;
    private Bitmap converted;
    private volatile int revision;
    private volatile boolean closed;
    private boolean showOriginal;
    private Runnable pending;

    public ColorConversionDialog(Activity activity,Bitmap source,int rotation,boolean cover,boolean cropped,String panel,
            PanelImage.Prepared initial,boolean binary,boolean dither,int block,int level,boolean protectGrays,Apply onApply,Runnable onClose){
        super(activity,android.R.style.Theme_Material_Light_NoActionBar);
        this.source=source;this.rotation=rotation;this.cover=cover;this.cropped=cropped;this.panel=panel;
        original=PanelImage.fitted(source,rotation,cover);converted=initial.preview;
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(10));root.setBackgroundColor(0xfff4f7f5);
        root.addView(label("墨水屏转换与预览",20));
        root.addView(label("双指缩放看细节；预览按实际发送像素还原。",12));
        preview=new ZoomPreview(activity,converted);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout viewTools=new LinearLayout(activity);
        compare=button("查看原图");compare.setOnClickListener(v->{showOriginal=!showOriginal;updateImage();});viewTools.addView(compare,weighted());
        Button fit=button("适应屏幕");fit.setOnClickListener(v->preview.fit());viewTools.addView(fit,weighted());
        Button actual=button("1:1 像素");actual.setOnClickListener(v->preview.actual());viewTools.addView(actual,weighted());root.addView(viewTools);
        status=label("正在生成预览…",12);root.addView(status);
        mode=spinner(new String[]{"四色 · 清晰色块","四色 · 照片抖动","黑白 · 文字","黑白 · 抖动"});mode.setSelection((binary?2:0)+(dither?1:0));root.addView(mode);
        blocks=spinner(new String[]{"细节：原始","细节：2×2 色块","细节：4×4 色块","细节：8×8 色块","细节：16×16 色块"});
        for(int i=0;i<blockSizes.length;i++)if(blockSizes[i]==block)blocks.setSelection(i);root.addView(blocks);
        protect=new CheckBox(activity);protect.setText("灰阶保护：灰色区域只用黑白，避免染红黄");protect.setChecked(protectGrays);root.addView(protect);
        thresholdLabel=label("黑白阈值："+level,12);root.addView(thresholdLabel);
        threshold=new SeekBar(activity);threshold.setMax(255);threshold.setProgress(level);root.addView(threshold);
        LinearLayout actions=new LinearLayout(activity);
        Button cancel=button("取消");cancel.setOnClickListener(v->dismiss());actions.addView(cancel,weighted());
        apply=button("应用到发送预览");apply.setTextColor(Color.WHITE);apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff176b61));
        apply.setOnClickListener(v->{boolean mono=mode.getSelectedItemPosition()>=2,noise=mode.getSelectedItemPosition()%2==1;int size=blockSizes[blocks.getSelectedItemPosition()],value=threshold.getProgress();boolean gray=protect.isChecked();dismiss();onApply.apply(mono,noise&&size==1,size,value,gray);});
        actions.addView(apply,new LinearLayout.LayoutParams(0,dp(48),2));root.addView(actions);setContentView(root);
        AdapterView.OnItemSelectedListener selected=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(blocks.getSelectedItemPosition()>0&&mode.getSelectedItemPosition()%2==1){mode.setSelection(mode.getSelectedItemPosition()-1);return;}schedule();}public void onNothingSelected(AdapterView<?> p){}};
        mode.setOnItemSelectedListener(selected);blocks.setOnItemSelectedListener(selected);
        protect.setOnCheckedChangeListener((b,checked)->schedule());
        threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){thresholdLabel.setText("黑白阈值："+value);if(user)schedule();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){schedule();}});
        setOnDismissListener(d->{closed=true;revision++;main.removeCallbacksAndMessages(null);worker.shutdownNow();onClose.run();});schedule();
    }
    @Override protected void onStart(){super.onStart();getWindow().setLayout(-1,-1);}
    private void schedule(){
        if(closed)return;
        showOriginal=false;updateImage();
        boolean binary=mode.getSelectedItemPosition()>=2;
        threshold.setVisibility(binary?View.VISIBLE:View.GONE);thresholdLabel.setVisibility(binary?View.VISIBLE:View.GONE);protect.setVisibility(binary?View.GONE:View.VISIBLE);
        final int token=++revision,selectedMode=mode.getSelectedItemPosition(),block=blockSizes[blocks.getSelectedItemPosition()],level=threshold.getProgress();final boolean gray=protect.isChecked();
        apply.setEnabled(false);status.setText("正在生成预览…");if(pending!=null)main.removeCallbacks(pending);
        pending=()->worker.execute(()->{
            if(closed||token!=revision)return;
            try{
                PanelImage.Prepared image=PanelImage.prepare(source,rotation,cover,selectedMode%2==1,block,selectedMode>=2,level,cropped,panel,gray);
                long[] counts=new long[4];for(byte value:image.packed)for(int bit=0;bit<8;bit+=2)counts[((value&255)>>bit)&3]++;
                final String summary=String.format(Locale.ROOT,"黑 %.1f%%  白 %.1f%%  黄 %.1f%%  红 %.1f%%",counts[0]*100.0/423936,counts[1]*100.0/423936,counts[2]*100.0/423936,counts[3]*100.0/423936);
                main.post(()->{if(closed||token!=revision)return;converted=image.preview;updateImage();status.setText(summary);apply.setEnabled(true);});
            }catch(Throwable e){main.post(()->{if(!closed&&token==revision)status.setText("转换失败："+e.getMessage());});}
        });main.postDelayed(pending,120);
    }
    private void updateImage(){preview.setImage(showOriginal?original:converted);compare.setText(showOriginal?"查看墨水屏效果":"查看原图");}
    private int dp(int value){return (int)(value*getContext().getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size){TextView view=new TextView(getContext());view.setText(text);view.setTextSize(size);view.setTextColor(0xff263e35);view.setPadding(0,dp(3),0,dp(3));return view;}
    private Button button(String text){Button view=new Button(getContext());view.setText(text);view.setTextSize(13);view.setAllCaps(false);return view;}
    private Spinner spinner(String[] values){Spinner view=new Spinner(getContext());view.setAdapter(new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_dropdown_item,values));return view;}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,dp(44),1);}
}
