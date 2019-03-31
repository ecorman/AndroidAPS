package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.Mockito.mock;

/**
 * Created by Rumen Georgiev on 8/31/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MsgStatusTempBasalTest {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        MsgStatusTempBasal packet = new MsgStatusTempBasal();
        DanaRPump pump = DanaRPump.getInstance();
        // test message decoding
        // Calling context.getResources() before super.onCreate() returns nullPointerException

    }
}