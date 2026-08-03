package net.kccricket.clicksorted.selftest;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Thrown by a {@link SelfTestCase} to report that a required capability (e.g. a dyed bundle
 * material added in a later Minecraft version) is unavailable on this server — a compatibility
 * finding, not a failure. {@link SelfTestRunner} catches this and records a skip.
 */
public class SelfTestSkip extends RuntimeException {
    public SelfTestSkip(String reason) {
        super(reason);
    }
}
