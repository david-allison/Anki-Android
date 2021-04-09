/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.backend;

import com.ichi2.async.CancelListener;
import com.ichi2.async.ProgressSender;

import net.ankiweb.rsdroid.BackendFactory;
import net.ankiweb.rsdroid.RustCleanup;

import java.util.List;

import BackendProto.Backend;

@SuppressWarnings( {"unused", "RedundantSuppression"})
@RustCleanup("Use this in Rust V2")
public class RustDroidV16Backend extends RustDroidBackend {
    public RustDroidV16Backend(BackendFactory backend) {
        super(backend);
    }

    @Override
    @RustCleanup("Needs testing - orderFromConfig usage vs Finder._order(Boolean)" +
            "We might want proto.SortOrder.BuiltinSearchOrder instead")
    public List<Long> findCards(String query, boolean orderFromConfig, CancelListener cancelListener, ProgressSender<Long> progressSender) {
        Backend.Empty empty = Backend.Empty.getDefaultInstance();

        Backend.SortOrder.Builder builder = Backend.SortOrder.newBuilder();

        if (orderFromConfig) {
            builder.setFromConfig(empty);
        } else {
            builder.setNone(empty);
        }

        Backend.SearchCardsOut searchCardsOut = mBackend.getBackend().searchCards(query, builder.build());

        List<Long> cardIdsList = searchCardsOut.getCardIdsList();

        for (Long id : cardIdsList) {
            progressSender.doProgress(id);
        }

        return cardIdsList;
    }
}
