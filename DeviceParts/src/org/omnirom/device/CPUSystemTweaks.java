/*
* Copyright (C) 2018 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.omnirom.device;

import android.content.Context;

public class CPUSystemTweaks {
    private static final String CPU0_SCALING_MIN_FREQ = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq";
    private static final String CPU4_SCALING_MIN_FREQ = "/sys/devices/system/cpu/cpu4/cpufreq/scaling_min_freq";

    public static void restore(Context context) {
        Utils.writeValue(CPU0_SCALING_MIN_FREQ, "300000");
        Utils.writeValue(CPU4_SCALING_MIN_FREQ, "300000");
    }
}
