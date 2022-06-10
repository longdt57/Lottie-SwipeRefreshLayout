# kotlin-ext
[![Version](https://jitpack.io/v/longdt57/Lottie-SwipeRefreshLayout.svg)](https://github.com/longdt57/Lottie-SwipeRefreshLayout/releases)

- SwipeRefreshLayout && Lottie



## Implementation
build.gradle
```
repositories {
  maven { url "https://jitpack.io" }
}

dependencies {
  implementation 'com.github.longdt57:Lottie-SwipeRefreshLayout:{version}'
}
```

## Usage
1 Xml
```
<lee.module.lottieswiperefreshlayout.LottieSwipeRefreshLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/lottieSwipeRefresh"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/darker_gray"
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    app:lottie_srl_indicator_overlay="false"
    app:lottie_srl_rawRes="@raw/sample_lottie">
    ...
    
</...>
```

// Attributes

```
<attr name="android:enabled" />
<!--    The Lottie is Overlay the content or not  -->
<attr name="lottie_srl_indicator_overlay" format="boolean" />
<attr name="lottie_srl_rawRes" format="reference" />
<!--    The Lottie Animation size    -->
<attr name="lottie_srl_size" format="dimension" />

<!--    The Spacing between Lottie View and Top.
This attr only works if lottie_srl_offset_end isn't defined.     -->
<attr name="lottie_srl_padding_top" format="dimension" />
<attr name="lottie_srl_padding_bottom" format="dimension" />

<attr name="lottie_srl_scale_enabled" format="boolean" />
<attr name="lottie_srl_alpha_enabled" format="boolean" />
```
