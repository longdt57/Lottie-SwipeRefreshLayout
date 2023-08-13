# LottieSwipeRefreshLayout
[![Version](https://jitpack.io/v/longdt57/Lottie-SwipeRefreshLayout.svg)](https://github.com/longdt57/Lottie-SwipeRefreshLayout/releases)

- Base on SwipeRefreshLayout 1.1.0: https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout

## Demo

| Default | indicatorOverlay | setColorScheme(Color) |
|-|-|-|
| <video src="https://user-images.githubusercontent.com/8809113/173092518-71497928-6aa3-435b-97ee-898cee0236b2.mp4" width=300 /> | <video src="https://user-images.githubusercontent.com/8809113/173169897-6504f5a9-39b9-4b02-80d6-9c5601db1239.mp4" width=300 /> | <video src="https://user-images.githubusercontent.com/8809113/173170142-f809595a-b950-4a48-bb83-fbdef12317fe.mp4" width=300 /> |

| setProgressVerticalPadding(px, px) | setSizePx(px)|
|-|-|
| <video src="https://user-images.githubusercontent.com/8809113/173170363-eb422021-162f-49fc-8c68-4e289a7c6954.mp4" width=300 /> | <video src="https://user-images.githubusercontent.com/8809113/173170096-6eef736f-0150-4352-b48d-e9acd205b1d3.mp4" width=300 /> |


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
    ...
    app:lottie_srl_rawRes="@raw/sample_lottie">
    ...
    
</...>
```

### Attributes

```
<!--    Default true. The Lottie is Overlay the content or not  -->
<attr name="lottie_srl_indicator_overlay" format="boolean" />

<!--    Default loader_...json. The lottie json raw file    -->
<attr name="lottie_srl_rawRes" format="reference" />

<!--    Default is 40dp. The Lottie Animation size    -->
<attr name="lottie_srl_size" format="dimension" />

<!--    Default true. If true, the tint color is Black if them light, or White if theme dard/night    -->
<attr name="lottie_srl_auto_tint" format="boolean" />

<!--    Default is 12dp. The Spacing between Lottie View and Top/bottom     -->
<attr name="lottie_srl_spacing_top" format="dimension" />
<attr name="lottie_srl_spacing_bottom" format="dimension" />

<!--    Default false. Additional animation    -->
<attr name="lottie_srl_scale_enabled" format="boolean" />
<attr name="lottie_srl_alpha_enabled" format="boolean" />
```

#### Customize/Default value
```
- var indicatorOverlay: Boolean = false // Whether to overlay the indicator on top of the content or not
- var autoTintColor: Boolean = false // Whether auto tint color by theme
- var mScale = false // Whether this item is scaled up rather than clipped
- var mAlpha = false // Whether this item is alpha up rather than clipped

- var lottieTopSpacing: Boolean = 12dp
- var lottieBottomSpacing = 12dp

- fun setSizePx(px) // lottieSize
- fun setProgressVerticalPadding(topSpacingPx, bottomSpacingPx): default 12dp, 12dp

```
