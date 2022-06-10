# kotlin-ext
[![Version](https://jitpack.io/v/longdt57/Lottie-SwipeRefreshLayout.svg)](https://github.com/longdt57/Lottie-SwipeRefreshLayout/releases)

- LottieSwipeRefreshLayout: base on SwipeRefreshLayout 1.1.0

## Demo


https://user-images.githubusercontent.com/8809113/173092518-71497928-6aa3-435b-97ee-898cee0236b2.mp4



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
### Xml
```
<lee.module.lottieswiperefreshlayout.LottieSwipeRefreshLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/lottieSwipeRefresh"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    app:lottie_srl_indicator_overlay="false"
    app:lottie_srl_rawRes="@raw/sample_lottie">
    ...
    
</...>
```

### Attributes

```
<attr name="android:enabled" />
<!--    The Lottie is Overlay the content or not  -->
<attr name="lottie_srl_indicator_overlay" format="boolean" />
<attr name="lottie_srl_rawRes" format="reference" />
<!--    The Lottie Animation size    -->
<attr name="lottie_srl_size" format="dimension" />

<!--    The Spacing between Lottie View and Top.     -->
<attr name="lottie_srl_padding_top" format="dimension" />
<attr name="lottie_srl_padding_bottom" format="dimension" />

<attr name="lottie_srl_scale_enabled" format="boolean" />
<attr name="lottie_srl_alpha_enabled" format="boolean" />
```

#### Default value
```
lottie_srl_indicator_overlay: false (true to not animate the content)
lottie_srl_size: 40dp
lottie_srl_padding_top: 12dp
lottie_srl_padding_bottom: 12dp
```

### Customize
- createLottieView: override to custom the lottie view
- setSizePx: Change the lottie size
- setColorScheme: Change the Lottie tint Color
