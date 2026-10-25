package com.campusfinance.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshPolicyTest {
    @Test fun selects120WhenSupported(){assertEquals(120f,RefreshPolicy.preferredRate(listOf(60f,90f,120f),true,false),0f)}
    @Test fun staysWithin120On144Panel(){assertEquals(120f,RefreshPolicy.preferredRate(listOf(60f,120f,144f),true,false),0f)}
    @Test fun supportsFractional120(){assertEquals(119.88f,RefreshPolicy.preferredRate(listOf(59.94f,119.88f),true,false),.001f)}
    @Test fun toleratesPanelRateRounding(){assertEquals(120f,RefreshPolicy.preferredRate(listOf(60.000004f,120.00001f),true,false),0f)}
    @Test fun supports90(){assertEquals(90f,RefreshPolicy.preferredRate(listOf(60f,90f),true,false),0f)}
    @Test fun doesNotInvent120On60Panel(){assertEquals(60f,RefreshPolicy.preferredRate(listOf(60f),true,false),0f)}
    @Test fun followsSystemWhenOff(){assertEquals(0f,RefreshPolicy.preferredRate(listOf(60f,120f),false,false),0f)}
    @Test fun releasesHighRefreshInPowerSave(){assertEquals(0f,RefreshPolicy.preferredRate(listOf(60f,120f),true,true),0f)}
    @Test fun invalidModesDoNotCrash(){assertEquals(0f,RefreshPolicy.preferredRate(listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f,0f),true,false),0f)}
    @Test fun noContinuousFramesNeededWhenIdle(){assertEquals(0f,RefreshPolicy.preferredRate(emptyList(),true,false),0f)}
    @Test fun motionSettingRemovesAnimationDuration(){assertEquals(0,DisplayState(animations=false).duration(240));assertEquals(0,DisplayState(systemAnimations=false).duration(240));assertEquals(240,DisplayState().duration(240))}
}
