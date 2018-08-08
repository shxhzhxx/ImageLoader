# ImageLoader
Library for loading, caching and displaying images on Android.



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

**Step 2.** Add the dependency

```
	dependencies {
	        implementation 'com.github.shxhzhxx:UrlLoader:1.0.2'
	}
```



## Usage

```java
//initialize in application.
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ImageLoader.init(this);
    }
}
```

```java
String url="https://image.yizhujiao.com/FiZr1lFxhobKLogy4pkTfLqv6xrV";
ImageView iv=findViewById(R.id.iv);
ImageLoader.getInstance().load(url).into(iv);
```

