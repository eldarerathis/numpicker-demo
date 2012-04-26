/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quietlycoding.android.picker;

import android.content.Context;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.EditText;

/**
 * This class has been pulled from the Android platform source code, its an internal widget that hasn't been
 * made public so its included in the project in this fashion for use with the preferences screen; I have made
 * a few slight modifications to the code here, I simply put a MAX and MIN default in the code but these values
 * can still be set publicly by calling code.
 *
 * @author Google
 */
public class NumberPicker extends LinearLayout implements OnClickListener,
        OnFocusChangeListener, OnLongClickListener {

    private static final double DEFAULT_MAX = 10;
    private static final double DEFAULT_MIN = 1;

    public interface OnChangedListener {
        void onChanged(NumberPicker picker, double oldVal, double newVal);
    }

    public interface Formatter {
        String toString(double value);
    }

    private NumberPicker.Formatter mFormatter =
            new NumberPicker.Formatter() {
                final StringBuilder mBuilder = new StringBuilder();
                final java.util.Formatter mFmt = new java.util.Formatter(mBuilder);
                final Object[] mArgs = new Object[1];
                public String toString(double value) {
                    mArgs[0] = value;
                    mBuilder.delete(0, mBuilder.length());
                    mFmt.format("%." + mPrecision + "f", mArgs);
                    return mFmt.toString();
                }
        };

    private final Handler mHandler;
    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (mIncrement) {
                changeCurrent(mCurrent + mStep);
                mHandler.postDelayed(this, mSpeed);
            } else if (mDecrement) {
                changeCurrent(mCurrent - mStep);
                mHandler.postDelayed(this, mSpeed);
            }
        }
    };
    
    private double mStep = 1;
    private double mEpsilon = 0.001; //Set a default
    private int mPrecision = 0;

    private final EditText mText;
    private final InputFilter mNumberInputFilter;

    protected double mStart;
    protected double mEnd;
    protected double mCurrent;
    protected double mPrevious;
    private OnChangedListener mListener;
    private long mSpeed = 300;

    private boolean mIncrement;
    private boolean mDecrement;

    public NumberPicker(Context context) {
        this(context, null);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.number_picker, this, true);
        mHandler = new Handler();
        InputFilter inputFilter = new NumberPickerInputFilter();
        mNumberInputFilter = new NumberRangeKeyListener();
        mIncrementButton = (NumberPickerButton) findViewById(R.id.increment);
        mIncrementButton.setOnClickListener(this);
        mIncrementButton.setOnLongClickListener(this);
        mIncrementButton.setNumberPicker(this);
        mDecrementButton = (NumberPickerButton) findViewById(R.id.decrement);
        mDecrementButton.setOnClickListener(this);
        mDecrementButton.setOnLongClickListener(this);
        mDecrementButton.setNumberPicker(this);

        mText = (EditText) findViewById(R.id.timepicker_input);
        mText.setOnFocusChangeListener(this);
        mText.setFilters(new InputFilter[] {inputFilter});
        mText.setRawInputType(InputType.TYPE_CLASS_NUMBER);

        if (!isEnabled()) {
            setEnabled(false);
        }

        mStart = DEFAULT_MIN;
        mEnd = DEFAULT_MAX;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mIncrementButton.setEnabled(enabled);
        mDecrementButton.setEnabled(enabled);
        mText.setEnabled(enabled);
    }

    public void setOnChangeListener(OnChangedListener listener) {
        mListener = listener;
    }

    public void setFormatter(Formatter formatter) {
        mFormatter = formatter;
    }

    /**
     * Set the range of numbers allowed for the number picker. The current
     * value will be automatically set to the start.
     *
     * @param start the start of the range (inclusive)
     * @param end the end of the range (inclusive)
     */
    public void setRange(int start, int end) {
        mStart = start;
        
        if (end == -1)
        	mEnd = Double.MAX_VALUE; //-1 indicates no maximum value
        else
        	mEnd = end;
        
        mCurrent = start;
        updateView();
    }
    
    public void setRange(double start, double end) {
    	mStart = start;
    	
    	if (Math.abs(end + 1) < mEpsilon)
    		mEnd = Double.MAX_VALUE; //-1 indicates no maximum value
    	else
    		mEnd = end;
    	
    	mCurrent = start;
    	updateView();
    }

    public void setCurrent(int current) {
        mCurrent = current;
        updateView();
    }
    
    public void setCurrent(float current)
    {
    	mCurrent = current;
    	updateView();
    }
    
    public void setPrecision(int places)
    {
    	if (places <= 0)
    	{
    		mEpsilon = 0.001;
    		mPrecision = 0;
    	}
    	else
    	{
	    	mEpsilon = 1.0;
	    	mPrecision = places;
	    	
	    	for (int i = 0; i < places; ++i)
	    		mEpsilon /= 10;
    	}
    }
    
    public void setStep(double step)
    {
    	mStep = step;
    }

    /**
     * The speed (in milliseconds) at which the numbers will scroll
     * when the the +/- buttons are longpressed. Default is 300ms.
     */
    public void setSpeed(long speed) {
        mSpeed = speed;
    }

    public void onClick(View v) {
        validateInput(mText);
        if (!mText.hasFocus()) mText.requestFocus();

        // now perform the increment/decrement
        if (R.id.increment == v.getId()) {
            changeCurrent(mCurrent + mStep);
        } else if (R.id.decrement == v.getId()) {
            changeCurrent(mCurrent - mStep);
        }
    }

    private String formatNumber(double value) {
        return (mFormatter != null)
                ? mFormatter.toString(value)
                : String.valueOf(value);
    }

    protected void changeCurrent(double current) {

        // Wrap around the values if we go past the start or end
        if (current > mEnd) {
            current = mStart;
        } else if (current < mStart) {
            current = mEnd;
        }
        mPrevious = mCurrent;
        mCurrent = current;

        notifyChange();
        updateView();
    }

    protected void notifyChange() {
        if (mListener != null) {
            mListener.onChanged(this, mPrevious, mCurrent);
        }
    }

    protected void updateView() {

        /* If we don't have displayed values then use the
         * current number else find the correct value in the
         * displayed values for the current number.
         */
        mText.setText(formatNumber(mCurrent));
        mText.setSelection(mText.getText().length());
    }

    private void validateCurrentView(CharSequence str) {
        double val = getSelectedPos(str.toString());
        if ((val >= mStart) && (val <= mEnd)) {
            if (Math.abs(mCurrent - val) > mEpsilon) {
                mPrevious = mCurrent;
                mCurrent = val;
                notifyChange();
            }
        }
        updateView();
    }

    public void onFocusChange(View v, boolean hasFocus) {

        /* When focus is lost check that the text field
         * has valid values.
         */
        if (!hasFocus) {
            validateInput(v);
        }
    }

    private void validateInput(View v) {
        String str = String.valueOf(((TextView) v).getText());
        if ("".equals(str)) {

            // Restore to the old value as we don't allow empty values
            updateView();
        } else {

            // Check the new value and ensure it's in range
            validateCurrentView(str);
        }
    }

    /**
     * We start the long click here but rely on the {@link NumberPickerButton}
     * to inform us when the long click has ended.
     */
    public boolean onLongClick(View v) {

        /* The text view may still have focus so clear it's focus which will
         * trigger the on focus changed and any typed values to be pulled.
         */
        mText.clearFocus();

        if (R.id.increment == v.getId()) {
            mIncrement = true;
            mHandler.post(mRunnable);
        } else if (R.id.decrement == v.getId()) {
            mDecrement = true;
            mHandler.post(mRunnable);
        }
        return true;
    }

    public void cancelIncrement() {
        mIncrement = false;
    }

    public void cancelDecrement() {
        mDecrement = false;
    }

    private static final char[] DIGIT_CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.'
    };

    private NumberPickerButton mIncrementButton;
    private NumberPickerButton mDecrementButton;

    private class NumberPickerInputFilter implements InputFilter {
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            return mNumberInputFilter.filter(source, start, end, dest, dstart, dend);
        }
    }

    private class NumberRangeKeyListener extends NumberKeyListener {

        // XXX This doesn't allow for range limits when controlled by a
        // soft input method!
        public int getInputType() {
            return InputType.TYPE_CLASS_NUMBER;
        }

        @Override
        protected char[] getAcceptedChars() {
            return DIGIT_CHARACTERS;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {

            CharSequence filtered = super.filter(source, start, end, dest, dstart, dend);
            if (filtered == null) {
                filtered = source.subSequence(start, end);
            }

            String result = String.valueOf(dest.subSequence(0, dstart))
                    + filtered
                    + dest.subSequence(dend, dest.length());

            if ("".equals(result)) {
                return result;
            }
            double val = getSelectedPos(result);

            /* Ensure the user can't type in a value greater
             * than the max allowed. We have to allow less than min
             * as the user might want to delete some numbers
             * and then type a new number.
             */
            if (val > mEnd || Math.abs(val - Double.MIN_VALUE) < mEpsilon) {
                return "";
            } else {
                return filtered;
            }
        }
    }

    private double getSelectedPos(String str) {
    	double retVal = Double.MIN_VALUE;
    	
    	try
    	{
            retVal = Double.parseDouble(str);
    	}
    	catch (NumberFormatException fEx)
    	{
    		//Invalid number entered, so just ignore it
    	}
    	
    	return retVal;
    }

    /**
     * @return the current value.
     */
    public int getCurrent() {
    	try
    	{
    		return Integer.parseInt(((EditText) findViewById(R.id.timepicker_input)).getText().toString());
    	}
    	catch (NumberFormatException fEx)
    	{
    		//Return the minimum value if we can't parse the EditText's value
    		return (int)mStart;
    	}
    }
    
    public double getFloatCurrent()
    {
    	try
    	{
    		return Double.parseDouble(((EditText) findViewById(R.id.timepicker_input)).getText().toString());
    	}
    	catch (NumberFormatException fEx)
    	{
    		//Return the minimum value if we can't parse the EditText's value
    		return mStart;
    	}
    }
}
