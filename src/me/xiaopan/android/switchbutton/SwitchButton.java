package me.xiaopan.android.switchbutton;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CompoundButton;
import android.widget.Scroller;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * Created by xiaopan on 2014/3/27 0027.
 */
public class SwitchButton extends CompoundButton {
    private int slideX = 0; //X轴当前位置，用于动态绘制图片显示位置，实现滑动效果
    private int minSlideX = 0;  //X轴最小位置，用于防止往左边滑动时超出范围
    private int maxSlideX = 0;  //X轴最大位置，用于防止往右边滑动时超出范围
    private int tempTotalSlideDistance;   //滑动距离，用于记录每次滑动的距离，在滑动结束后根据距离判断是否切换状态或者回滚
    private int duration = 200;
    private float tempTouchX;   //记录上次触摸位置，用于计算滑动距离
    private float minChangeDistanceScale = 0.2f;   //有效距离比例，例如按钮宽度为100，比例为0.3，那么只有当滑动距离大于等于(100*0.3)才会切换状态，否则就回滚
    private Paint paint;    //画笔，用来绘制遮罩效果
    private Drawable frameDrawable; //框架层图片
    private Drawable statusDrawable;    //状态图片
    private BitmapDrawable statusMaskBitmapDrawable;    //状态遮罩图片
    private Drawable sliderDrawable;    //滑块图片
    private BitmapDrawable sliderMaskBitmapDrawable;    //滑块遮罩图片
    private SwitchScroller switchScroller;
    private PorterDuffXfermode porterDuffXfermode;//遮罩类型

    public SwitchButton(Context context) {
        this(context, null);
    }

    public SwitchButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SwitchButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs){
        paint = new Paint();
        paint.setColor(Color.RED);
        porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        switchScroller = new SwitchScroller(getContext(), new AccelerateDecelerateInterpolator());

        if(attrs != null){
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.SwitchButton);
            setDrawables(
                typedArray.getDrawable(R.styleable.SwitchButton_frameDrawable),
                typedArray.getDrawable(R.styleable.SwitchButton_stateDrawable),
                (BitmapDrawable) typedArray.getDrawable(R.styleable.SwitchButton_stateMaskDrawable),
                typedArray.getDrawable(R.styleable.SwitchButton_sliderDrawable),
                (BitmapDrawable) typedArray.getDrawable(R.styleable.SwitchButton_sliderMaskDrawable)
            );
            typedArray.recycle();
        }
        setChecked(isChecked());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //计算宽度
        int measureWidth;
        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.AT_MOST://如果widthSize是当前视图可使用的最大宽度
                measureWidth = (frameDrawable != null? frameDrawable.getIntrinsicWidth():0) + getPaddingLeft() + getPaddingRight();
                break;
            case MeasureSpec.EXACTLY://如果widthSize是当前视图可使用的绝对宽度
                measureWidth = MeasureSpec.getSize(widthMeasureSpec);
                break;
            case MeasureSpec.UNSPECIFIED://如果widthSize对当前视图宽度的计算没有任何参考意义
                measureWidth = (frameDrawable != null? frameDrawable.getIntrinsicWidth():0) + getPaddingLeft() + getPaddingRight();
                break;
            default:
                measureWidth = (frameDrawable != null? frameDrawable.getIntrinsicWidth():0) + getPaddingLeft() + getPaddingRight();
                break;
        }

        //计算高度
        int measureHeight;
        switch (MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.AT_MOST://如果heightSize是当前视图可使用的最大宽度
                measureHeight = (frameDrawable != null? frameDrawable.getIntrinsicHeight():0) + getPaddingTop() + getPaddingBottom();
                break;
            case MeasureSpec.EXACTLY://如果heightSize是当前视图可使用的绝对宽度
                measureHeight = MeasureSpec.getSize(heightMeasureSpec);
                break;
            case MeasureSpec.UNSPECIFIED://如果heightSize对当前视图宽度的计算没有任何参考意义
                measureHeight = (frameDrawable != null? frameDrawable.getIntrinsicHeight():0) + getPaddingTop() + getPaddingBottom();
                break;
            default:
                measureHeight = (frameDrawable != null? frameDrawable.getIntrinsicHeight():0) + getPaddingTop() + getPaddingBottom();
                break;
        }

        setMeasuredDimension(measureWidth, measureHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if(changed){

        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //保存并创建一个新的透明层，如果不这样做的话，画出来的背景会是黑的
        canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.MATRIX_SAVE_FLAG | Canvas.CLIP_SAVE_FLAG | Canvas.HAS_ALPHA_LAYER_SAVE_FLAG | Canvas.FULL_COLOR_LAYER_SAVE_FLAG | Canvas.CLIP_TO_LAYER_SAVE_FLAG);

        //绘制状态层
        if(statusDrawable != null && statusMaskBitmapDrawable != null){
            canvas.save();
            canvas.translate(slideX, 0);
            statusDrawable.draw(canvas);
            canvas.restore();
            paint.setXfermode(porterDuffXfermode);
            canvas.drawBitmap(statusMaskBitmapDrawable.getBitmap(), 0, 0, paint);
            paint.setXfermode(null);
        }

        //绘制框架层
        if(frameDrawable != null){
            frameDrawable.draw(canvas);
        }

        //绘制滑块层
        if(sliderDrawable != null && sliderMaskBitmapDrawable != null){
            canvas.save();
            canvas.translate(slideX, 0);
            sliderDrawable.draw(canvas);
            canvas.restore();
            paint.setXfermode(porterDuffXfermode);
            canvas.drawBitmap(sliderMaskBitmapDrawable.getBitmap(), 0, 0, paint);
            paint.setXfermode(null);
        }

        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if(isEnabled()){
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN :
                    tempTotalSlideDistance = 0; //清空总滑动距离
                    tempTouchX = event.getX();  //记录X轴位置
                    setPressed(true);   //激活按下状态
                    break;
                case MotionEvent.ACTION_MOVE :
                    float newTouchX = event.getX();
                    int currentDistance = (int) (newTouchX - tempTouchX);   //计算本次滑动距离
                    tempTotalSlideDistance += currentDistance;    //记录总滑动距离
                    setSlideX(slideX += currentDistance);   //更新X轴位置
                    tempTouchX = newTouchX; //记录X轴位置
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP :
                    setPressed(false);  //取消按下状态
                    if(Math.abs(tempTotalSlideDistance) >= 10){//当滑动距离大于0才会被认为这是一次有效的滑动操作，否则就是单机操作
                        if(Math.abs(tempTotalSlideDistance) >= Math.abs(frameDrawable.getIntrinsicWidth() * minChangeDistanceScale)){//如果滑动距离大于等于最小切换距离就切换状态
                            setChecked(!isChecked());   //切换状态
                        }else{
                            switchScroller.startScroll(isChecked());//本次滑动无效，回滚
                        }
                    }else{
                        setChecked(!isChecked());   //单击切换状态
                    }
                    break;
                case MotionEvent.ACTION_CANCEL :
                    System.out.println("MOVE");
                    switchScroller.startScroll(isChecked()); //回滚
                    break;
                case MotionEvent.ACTION_OUTSIDE :
                    System.out.println("MOVE");
                    switchScroller.startScroll(isChecked()); //回滚
                    break;
            }
            return true;
        }else{
            return false;
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        int[] drawableState = getDrawableState();
        if(frameDrawable != null){
            frameDrawable.setState(drawableState);  //更新框架图片的状态
        }
        if(statusDrawable != null){
            statusDrawable.setState(drawableState); //更新状态图片的状态
        }
        if(sliderDrawable != null){
            sliderDrawable.setState(drawableState); //更新滑块图片的状态
        }
        invalidate();
    }

    @Override
     public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        if(changed){
            if(getWidth() > 0 && switchScroller != null){   //如果本次执行不是在onCreate()中
                switchScroller.startScroll(checked);
            }else{
                setSlideX(isChecked()?minSlideX:maxSlideX);  //直接修改X轴位置
            }
        }
    }

    /**
     * 设置图片
     * @param frameBitmap 框架图片
     * @param statusDrawable 状态图片
     * @param statusMaskBitmapDrawable 状态遮罩图片
     * @param sliderDrawable 滑块图片
     * @param sliderMaskBitmapDrawable 滑块遮罩图片
     */
    public void setDrawables(Drawable frameBitmap, Drawable statusDrawable, BitmapDrawable statusMaskBitmapDrawable, Drawable sliderDrawable, BitmapDrawable sliderMaskBitmapDrawable){
        if(frameBitmap == null || statusDrawable == null || statusMaskBitmapDrawable == null || sliderDrawable == null || sliderMaskBitmapDrawable == null){
            throw new IllegalArgumentException("ALL NOT NULL");
        }

        this.frameDrawable = frameBitmap;
        this.statusDrawable = statusDrawable;
        this.statusMaskBitmapDrawable = statusMaskBitmapDrawable;
        this.sliderDrawable = sliderDrawable;
        this.sliderMaskBitmapDrawable = sliderMaskBitmapDrawable;

        this.frameDrawable.setBounds(0, 0, this.frameDrawable.getIntrinsicWidth(), this.frameDrawable.getIntrinsicHeight());
        this.frameDrawable.setCallback(this);
        this.statusDrawable.setBounds(0, 0, this.statusDrawable.getIntrinsicWidth(), this.statusDrawable.getIntrinsicHeight());
        this.statusDrawable.setCallback(this);
        this.sliderDrawable.setBounds(0, 0, this.sliderDrawable.getIntrinsicWidth(), this.sliderDrawable.getIntrinsicHeight());
        this.sliderDrawable.setCallback(this);

        this.minSlideX = (-1 * (statusDrawable.getIntrinsicWidth() - frameBitmap.getIntrinsicWidth()));  //初始化X轴最小值
        setSlideX(isChecked()?minSlideX:maxSlideX);  //根据选中状态初始化默认位置

        requestLayout();
    }

    /**
     * 设置图片
     * @param frameDrawableResId 框架图片ID
     * @param statusDrawableResId 状态图片ID
     * @param statusMaskDrawableResId 状态遮罩图片ID
     * @param sliderDrawableResId 滑块图片ID
     * @param sliderMaskDrawableResId 滑块遮罩图片ID
     */
    public void setDrawableResIds(int frameDrawableResId, int statusDrawableResId, int statusMaskDrawableResId, int sliderDrawableResId, int sliderMaskDrawableResId){
        setDrawables(
            getResources().getDrawable(frameDrawableResId),
            getResources().getDrawable(statusDrawableResId),
            (BitmapDrawable) getResources().getDrawable(statusMaskDrawableResId),
            getResources().getDrawable(sliderDrawableResId),
            (BitmapDrawable) getResources().getDrawable(sliderMaskDrawableResId)
        );
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * 设置X轴位置
     * @param slideX
     */
    private void setSlideX(int slideX) {
        this.slideX = slideX;
        if(this.slideX < minSlideX){
            this.slideX = minSlideX;
        }

        if(this.slideX > maxSlideX){
            this.slideX = maxSlideX;
        }
    }

    /**
     * 切换滚动器，用于实现滚动动画
     */
    private class SwitchScroller implements Runnable {
        private Scroller scroller;

        public SwitchScroller(Context context, android.view.animation.Interpolator interpolator) {
            this.scroller = new Scroller(context, interpolator);
        }

        /**
         * 开始滚动
         * @param checked 是否选中
         */
        public void startScroll(boolean checked){
            scroller.startScroll(slideX, 0, (checked?minSlideX:maxSlideX) - slideX, 0, duration);
            post(this);
        }

        @Override
        public void run() {
            if(scroller.computeScrollOffset()){
                setSlideX(scroller.getCurrX());
                invalidate();
                post(this);
            }
        }
    }
}
