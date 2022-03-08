/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jhlabs.composite;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public abstract class RGBComposite implements Composite {
    protected float extraAlpha;

    protected RGBComposite() {
        this(1.0f);
    }

    protected RGBComposite(float alpha) {
        if (alpha < 0.0f || alpha > 1.0f) {
            throw new IllegalArgumentException("RGBComposite: alpha must be between 0 and 1");
        }
        extraAlpha = alpha;
    }

    public float getAlpha() {
        return extraAlpha;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(extraAlpha);
    }

    public void setExtraAlpha(float extraAlpha) {
        this.extraAlpha = extraAlpha;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RGBComposite)) {
            return false;
        }
        RGBComposite c = (RGBComposite) o;

        return extraAlpha == c.extraAlpha;
    }

    public abstract static class RGBCompositeContext implements CompositeContext {
        private final float alpha;
//        private final ColorModel srcColorModel;
//        private final ColorModel dstColorModel;

        protected RGBCompositeContext(float alpha, ColorModel srcColorModel, ColorModel dstColorModel) {
            this.alpha = alpha;

            assert srcColorModel.getNumComponents() == 4 :
                "# src components = " + srcColorModel.getNumComponents();
            assert dstColorModel.getNumComponents() == 4 :
                "# dst components = " + dstColorModel.getNumComponents();

//            this.srcColorModel = srcColorModel;
//            this.dstColorModel = dstColorModel;
        }

        @Override
        public void dispose() {
        }

        // Multiply two numbers in the range 0..255 such that 255*255=255
        static int multiply255(int a, int b) {
            int t = a * b + 0x80;
            return ((t >> 8) + t) >> 8;
        }

        static int clamp(int a) {
            return a < 0 ? 0 : a > 255 ? 255 : a;
        }

        public abstract void composeRGB(int[] src, int[] dst, float alpha);

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            float alpha = this.alpha;

            int[] srcPix = null;
            int[] dstPix = null;

            int x = dstOut.getMinX();
            int w = dstOut.getWidth();
            int y0 = dstOut.getMinY();
            int y1 = y0 + dstOut.getHeight();

            for (int y = y0; y < y1; y++) {
                srcPix = src.getPixels(x, y, w, 1, srcPix);
                dstPix = dstIn.getPixels(x, y, w, 1, dstPix);
//                int srclength = srcPix.length;
//                int dstlength = dstPix.length;
//                if(srclength > dstlength) {
//                    continue;
//                }
                // System.out.println("RGBComposite$RGBCompositeContext.compose dstlength = " + dstlength + ", srclength = " + srclength);
                composeRGB(srcPix, dstPix, alpha);
                dstOut.setPixels(x, y, w, 1, dstPix);
            }
        }
    }
}
