package com.jarman.triplepressunlock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockBackgroundImageLoaderTest {
    @Test
    fun landscapeImageIsSizedToCoverLandscapeScreen() {
        assertEquals(1920 to 1440, LockBackgroundImageLoader.centerCropDecodeSize(4000, 3000, 1920, 1080))
    }

    @Test
    fun portraitImageIsSizedToCoverLandscapeScreen() {
        assertEquals(1920 to 2880, LockBackgroundImageLoader.centerCropDecodeSize(4000, 6000, 1920, 1080))
    }

    @Test
    fun smallImagesAreNotUpscaledDuringDecode() {
        assertEquals(800 to 600, LockBackgroundImageLoader.centerCropDecodeSize(800, 600, 1920, 1080))
    }

    @Test
    fun bitmapFactoryUsesPowerOfTwoSampling() {
        assertEquals(4, LockBackgroundImageLoader.calculateInSampleSize(7680, 4320, 1920, 1080))
    }
}
