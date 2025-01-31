/*
 * Copyright (c) 2009--2017 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.domain.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.rhn.domain.Label;
import com.redhat.rhn.testing.TestUtils;

import org.junit.jupiter.api.Test;


/**
 * LabelTest
 */
public class LabelTest  {

    class BeerLabel extends Label {
        private String beerLabel;
        private String beerName;

        BeerLabel(String name, String label) {
            this.beerLabel = label;
            this.beerName = name;
        }

        @Override
        public String getName() {
            return beerName;
        }

        @Override
        public String getLabel() {
            return beerLabel;
        }
    }

    /**
     * Test method for {@link com.redhat.rhn.domain.Label#equals(java.lang.Object)}.
     */
    @Test
    public void testEqualsObject() {
        BeerLabel negroModelo = new BeerLabel("Negro Model",
                "Negro Modelo....what beer was meant to be");
        BeerLabel anotherNegroModelo = new BeerLabel("Negro Model",
                "Negro Modelo....what beer was meant to be");
        BeerLabel aprihop = new BeerLabel("Aprihop",
                "Aprihop....Dogfish Head bringing you America's finest beer");

        assertEquals(true, TestUtils.equalTest(negroModelo, anotherNegroModelo));
        assertEquals(false, TestUtils.equalTest(negroModelo, aprihop));
        assertEquals(false, TestUtils.equalTest(negroModelo, new Object()));
    }

}
