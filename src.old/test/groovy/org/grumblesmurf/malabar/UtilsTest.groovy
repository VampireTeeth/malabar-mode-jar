/**
 * Copyright (c) 2009, 2010 Espen Wiborg <espenhw@grumblesmurf.org>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */ 
package org.grumblesmurf.malabar;

import org.junit.Test;

import static org.junit.Assert.*
import static org.junit.matchers.JUnitMatchers.*
import static org.hamcrest.CoreMatchers.*
import static net.java.quickcheck.generator.PrimitiveGenerators.*
import static net.java.quickcheck.generator.CombinedGenerators.*
import static net.java.quickcheck.QuickCheck.*
import static net.java.quickcheck.generator.iterable.Iterables.*

class UtilsTest 
{
    @Test
    void emptyListIsEmpty() {
        assertThat(Utils.asLispList([]), is("()"))
    }

    @Test
    void singletonList() {
        assertThat(Utils.asLispList(['a']), is('("a")'))
    }

    @Test
    void nestedList() {
        assertThat(Utils.asLispList(['a', ['b'], 'c']), is('("a" ("b") "c")'))
    }

    /**
     * Utils.asLisp
     * passed a list of strings
     * Properties:
     *  # Starts with a paren (
     *  # Ends with a paren )
     *  # contains all the elements in the list
     *  # elements are in the same order
     *  # Each element is in double quotes
     */
    @Test
    void asLispWithStringGenerated () {
        for (List<String> words : toIterable( nonEmptyLists(strings()))) {
            String result = Utils.asLisp(words);
            assertEquals(true, result.endsWith(")"));
            assertEquals(true, result.startsWith("("));
            int index = -1;
            for(String word: words) {
                String w = "\"" + word + "\"";
                int i = result.indexOf(w , index);
                assertTrue ("word not found in increasing order: word: [" + w + "] i:" + i + " index:" + index+ " result:" + result, 
                                i > index);
                index = i + word.length() + 2 // for the quotes
            }
        }
    }
}
