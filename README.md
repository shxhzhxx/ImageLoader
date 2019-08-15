# ImageLoader
Library for loading, caching and displaying images on Android.

https://github.com/shxhzhxx/UrlLoader



## Dependency

**Step 1.** Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

**Step 2.** Add the dependency<br>
[![](https://www.jitpack.io/v/shxhzhxx/ImageLoader.svg)](https://www.jitpack.io/#shxhzhxx/ImageLoader)


```
	dependencies {
	        implementation 'com.github.shxhzhxx:UrlLoader:2.1.6'
	}
```



## Usage

```kotlin
val loader = ImageLoader(contentResolver, cacheDir)
loader.load(imageView,url)
```

