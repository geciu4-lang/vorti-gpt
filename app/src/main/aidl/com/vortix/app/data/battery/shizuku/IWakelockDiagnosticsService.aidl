package com.vortix.app.data.battery.shizuku;

interface IWakelockDiagnosticsService {
    String readWakelockCheckin();
    void destroy();
    boolean forceStopPackage(String packageName);
    boolean restrictBackground(String packageName);
}
