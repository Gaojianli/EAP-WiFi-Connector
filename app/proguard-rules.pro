# Keep the reflected @hide WifiEnterpriseConfig.setCaPath used for system CA validation.
-keepclassmembers class android.net.wifi.WifiEnterpriseConfig {
    public void setCaPath(java.lang.String);
}
