/*
 * Storage.java
 *
 * Copyright (C) 2012-2014 LucasEasedUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.lucaseasedup.logit.storage;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class Storage implements AutoCloseable
{
    public abstract void connect() throws IOException;
    public abstract boolean isConnected() throws IOException;
    public abstract void ping() throws IOException;
    
    @Override
    public abstract void close() throws IOException;
    
    public abstract List<String> getUnitNames() throws IOException;
    public abstract Hashtable<String, DataType> getKeys(String unit) throws IOException;
    
    public abstract List<Storage.Entry> selectEntries(String unit) throws IOException;
    public abstract List<Storage.Entry> selectEntries(String unit,
                                                      List<String> keys) throws IOException;
    public abstract List<Storage.Entry> selectEntries(String unit,
                                                      Selector selector) throws IOException;
    public abstract List<Storage.Entry> selectEntries(String unit,
                                                      List<String> keys,
                                                      Selector selector) throws IOException;
    
    public abstract void createUnit(String unit, Hashtable<String, DataType> keys)
            throws IOException;
    public abstract void renameUnit(String unit, String newName) throws IOException;
    public abstract void eraseUnit(String unit) throws IOException;
    public abstract void removeUnit(String unit) throws IOException;
    
    public abstract void addKey(String unit, String key, DataType type) throws IOException;
    public abstract void addEntry(String unit, Storage.Entry entry) throws IOException;
    public abstract void updateEntries(String unit,
                                       Storage.Entry entrySubset,
                                       Selector selector) throws IOException;
    public abstract void removeEntries(String unit, Selector selector) throws IOException;
    
    public abstract void executeBatch() throws IOException;
    public abstract void clearBatch() throws IOException;
    
    public boolean isAutobatchEnabled()
    {
        return autobatch;
    }
    
    public void setAutobatchEnabled(boolean status)
    {
        autobatch = status;
    }
    
    public enum DataType
    {
        /**
         * Integer-number value.
         */
        INTEGER,
        
        /**
         * Real-number value.
         */
        REAL,
        
        /**
         * Text of maximum length of 255 characters.
         */
        TINYTEXT,
        
        /**
         * Text of maximum length of 1023 characters.
         */
        MEDIUMTEXT,
        
        /**
         * Text of maximum length of 10119 characters.
         */
        LONGTEXT,
        
        /**
         * Text of unlimited length.
         */
        TEXT;
    }
    
    public static final class Entry implements Iterable<Storage.Entry.Datum>
    {
        public String get(String key)
        {
            if (key == null || key.trim().isEmpty())
                throw new IllegalArgumentException();
            
            return backend.get(key);
        }
        
        public void put(String key, String value)
        {
            if (key == null || key.trim().isEmpty())
                throw new IllegalArgumentException();
            
            if (value == null)
            {
                backend.put(key, "");
            }
            else
            {
                backend.put(key, value);
            }
        }
        
        public boolean containsKey(String key)
        {
            return get(key) != null;
        }
        
        public Storage.Entry copy()
        {
            Storage.Entry copy = new Storage.Entry();
            
            copy.backend = new LinkedHashMap<>(backend);
            
            return copy;
        }
        
        @Override
        public Iterator<Datum> iterator()
        {
            return new DatumIterator();
        }
        
        public final class DatumIterator implements Iterator<Datum>
        {
            public DatumIterator()
            {
                it = backend.entrySet().iterator();
            }
            
            @Override
            public boolean hasNext()
            {
                return it.hasNext();
            }
            
            @Override
            public Datum next()
            {
                Map.Entry<String, String> el = it.next();
                
                return new Datum(el.getKey(), el.getValue());
            }
            
            @Override
            public void remove()
            {
                it.remove();
            }
            
            private final Iterator<Map.Entry<String, String>> it;
        }
        
        public static final class Datum
        {
            private Datum(String key, String value)
            {
                if (key == null || key.trim().isEmpty())
                    throw new IllegalArgumentException();
                
                this.key = key;
                
                if (value == null)
                {
                    this.value = "";
                }
                else
                {
                    this.value = value;
                }
            }
            
            public String getKey()
            {
                return key;
            }
            
            public String getValue()
            {
                return value;
            }
            
            private final String key;
            private final String value;
        }
        
        public static final class Builder
        {
            public Builder()
            {
                entry = new Storage.Entry();
            }
            
            public Builder put(String key, String value)
            {
                entry.put(key, value);
                
                return this;
            }
            
            public Storage.Entry build()
            {
                Storage.Entry builtEntry = entry;
                
                entry = new Storage.Entry();
                
                return builtEntry;
            }
            
            private Storage.Entry entry;
        }
        
        private Map<String, String> backend = new LinkedHashMap<>();
    }
    
    private boolean autobatch = false;
}
