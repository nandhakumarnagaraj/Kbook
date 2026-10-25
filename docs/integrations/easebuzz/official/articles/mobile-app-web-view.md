---
stoplight-id: 1c1ekwms19c6l
---

# Mobile App Web View
---

Enable UPI Intent in WebView on your Android application.

To enable UPI Intent flow in Web view of your mobile application with Web Integration or Iframe Integration.

- Follow **[Go-live](Integration-flow.md)** Steps 
- Complete your **[Web Integration](Web-integration.md)** or **[Iframe Integration](2.1-iFrame-Integration.md)**.
- Find the sample Android native code for handling intent flow in WebView. Use this code to implement the intent flow within the Android native WebView.

```java

@Override
public boolean shouldOverrideUrlLoading(WebView webView, String url) {
    if (url.startsWith("http:") || url.startsWith("https:")) {
        return false;
    }
    if (url.startsWith("upi://pay?")) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }
    return true;
}

```

> **Note:** If you are using other coding languages other than Android native you have to build the same logic in your application.





---