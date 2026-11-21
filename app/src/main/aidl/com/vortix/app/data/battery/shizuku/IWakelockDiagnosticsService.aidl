package com.vortix.app.data.battery.shizuku;

interface IWakelockDiagnosticsService {
    String readWakelockCheckin();
    void destroy();
}
