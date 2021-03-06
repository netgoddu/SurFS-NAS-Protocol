/*
 * Copyright (C) 2016 SurCloud.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * http://www.gnu.org/licenses/licenses.html
 */ 

package com.surfs.nas.mnt;

import com.surfs.nas.StoragePool;
import com.surfs.nas.StorageSources;
import java.io.IOException;
import java.util.Map;

public class SurDevicePermission extends Thread {

    private String name = null;
    private Map<String, String> map;

    public SurDevicePermission(String name) throws IOException {
        this.name = name;
        load();
    }

    private synchronized void load() throws IOException {
        StoragePool pool = StorageSources.getStoragePool(SurNasDriver.poolname);
        map = pool.getDatasource().getNasMetaAccessor().getPermission(name);
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            try {
                sleep(1000 * 60 * 10);
                load();
            } catch (IOException ex) {
            } catch (InterruptedException ex) {
                break;
            }
        }
    }

    /**
     *
     * @param username
     * @return
     */
    public String getPermission(String username) {
        String per = map.get(username);
        if (per == null) {
            try {
                load();
            } catch (IOException ex) {
            }
            return map.get(username);
        } else {
            return per;
        }
    }
}
