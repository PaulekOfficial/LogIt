package io.github.lucaseasedup.logit.storage;

import io.github.lucaseasedup.logit.LogItCore;
import io.github.lucaseasedup.logit.logging.CustomLevel;
import io.github.lucaseasedup.logit.util.CollectionUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class WrapperStorage implements Storage
{
    private WrapperStorage(Storage leading, CacheType cacheType)
    {
        if (leading == null || cacheType == null)
            throw new IllegalArgumentException();
        
        this.executorService = Executors.newSingleThreadExecutor();
        this.leading = leading;
        this.cacheType = cacheType;
        
        if (cacheType == CacheType.PRELOADED)
        {
            preloadedCache = new HashMap<>();
        }
    }
    
    @Override
    public synchronized void connect() throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#connect()");
        
        leading.connect();
        
        for (Storage mirror : mirrors.keySet())
        {
            mirror.connect();
        }
    }
    
    @Override
    public synchronized boolean isConnected() throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#isConnected()");
        
        return leading.isConnected();
    }
    
    public synchronized void preload(String... units) throws IOException
    {
        if (units == null)
            throw new IllegalArgumentException();
        
        if (cacheType == CacheType.PRELOADED)
        {
            preloadedCache.clear();
            
            for (String unit : units)
            {
                PreloadedUnitCache unitCache = new PreloadedUnitCache(
                        leading.getKeys(unit),
                        leading.getPrimaryKey(unit),
                        leading.selectEntries(unit)
                );
                
                preloadedCache.put(unit, unitCache);
            }
        }
    }
    
    @Override
    public synchronized void ping() throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#ping()");
        
        executorService.submit(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    leading.ping();
                }
                catch (IOException ex)
                {
                    log(Level.WARNING, ex);
                }
                
                for (Storage mirror : mirrors.keySet())
                {
                    try
                    {
                        mirror.ping();
                    }
                    catch (IOException ex)
                    {
                        log(Level.WARNING, ex);
                    }
                }
            }
        });
    }
    
    @Override
    public synchronized void close() throws IOException
    {
        for (StorageObserver observer : observers)
        {
            observer.beforeClose();
        }
        
        log(CustomLevel.INTERNAL, "WrapperStorage#close()");
        
        leading.close();
        
        for (Storage mirror : mirrors.keySet())
        {
            mirror.close();
        }
    }
    
    @Override
    public synchronized List<String> getUnitNames() throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#getUnitNames()");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.getUnitNames();
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            return new ArrayList<>(preloadedCache.keySet());
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized UnitKeys getKeys(String unit)
            throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#getKeys(\"" + unit + "\")");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.getKeys(unit);
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            return preloadedCache.get(unit).getKeys();
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized String getPrimaryKey(String unit) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#getPrimaryKey(\"" + unit + "\")");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.getPrimaryKey(unit);
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            return preloadedCache.get(unit).getPrimaryKey();
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized List<StorageEntry> selectEntries(String unit)
            throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#selectEntries(\"" + unit + "\")");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.selectEntries(unit);
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            List<StorageEntry> entries = preloadedCache.get(unit).getEntryList();
            
            if (entries == null)
                return null;
            
            return StorageEntry.copyList(entries);
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized List<StorageEntry> selectEntries(
            String unit, Selector selector
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#selectEntries("
                + "\"" + unit + "\", "
                + SqlUtils.translateSelector(selector, "`", "'") + ")");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.selectEntries(unit, selector);
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            List<StorageEntry> entries = preloadedCache.get(unit).getEntryList();
            
            if (entries == null)
                return null;
            
            return StorageEntry.copyList(
                    entries, new SelectorConstant(true)
            );
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized List<StorageEntry> selectEntries(
            String unit, List<String> keys
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#selectEntries("
                + "\"" + unit + "\", "
                + Arrays.toString(keys.toArray()) + ")");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.selectEntries(unit, keys);
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            List<StorageEntry> entries = preloadedCache.get(unit).getEntryList();
            
            if (entries == null)
                return null;
            
            return StorageEntry.copyList(
                    entries, keys, new SelectorConstant(true)
            );
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized List<StorageEntry> selectEntries(
            String unit, List<String> keys, Selector selector
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#selectEntries("
                + "\"" + unit + "\", "
                + Arrays.toString(keys.toArray()) + ", "
                + SqlUtils.translateSelector(selector, "`", "'") + ")");
        
        if (cacheType == CacheType.DISABLED)
        {
            return leading.selectEntries(unit, keys, selector);
        }
        else if (cacheType == CacheType.PRELOADED)
        {
            List<StorageEntry> entries = preloadedCache.get(unit).getEntryList();
            
            if (entries == null)
                return null;
            
            return StorageEntry.copyList(
                    entries, keys, selector
            );
        }
        else
        {
            throw new RuntimeException("Unsupported cache type: " + cacheType);
        }
    }
    
    @Override
    public synchronized void createUnit(
            String unit, final UnitKeys keys, final String primaryKey
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#createUnit("
                + "\"" + unit + "\", "
                + "UnitKeys {keys: ["
                        + CollectionUtils.toString(keys.keySet())
                + "]})");
        
        leading.createUnit(unit, keys, primaryKey);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                storage.createUnit(unit, keys, primaryKey);
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (!preloadedCache.containsKey(unit))
            {
                PreloadedUnitCache unitCache = new PreloadedUnitCache(
                        keys, primaryKey, new LinkedList<StorageEntry>()
                );
                
                preloadedCache.put(unit, unitCache);
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterCreateUnit(unit, keys);
        }
    }
    
    @Override
    public synchronized void renameUnit(String unit, String newName)
            throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#renameUnit("
                + "\"" + unit + "\", "
                + "\"" + newName + "\")");
        
        if (unit.equals(newName))
            throw new IllegalArgumentException();
        
        leading.renameUnit(unit, newName);
        
        for (Map.Entry<Storage, Map<String, String>> e : mirrors.entrySet())
        {
            String unitMapping = e.getValue().get(unit);
            
            if (unitMapping == null)
            {
                unitMapping = unit;
            }
            
            e.getValue().remove(unit);
            e.getValue().put(newName, unitMapping);
        }
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                preloadedCache.put(newName, preloadedCache.remove(unit));
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterRenameUnit(unit, newName);
        }
    }
    
    @Override
    public synchronized void eraseUnit(String unit) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#eraseUnit("
                + "\"" + unit + "\")");
        
        leading.eraseUnit(unit);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                storage.eraseUnit(unit);
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                preloadedCache.get(unit).getEntryList().clear();
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterEraseUnit(unit);
        }
    }
    
    @Override
    public synchronized void removeUnit(String unit) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#removeUnit("
                + "\"" + unit + "\")");
        
        leading.removeUnit(unit);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                storage.removeUnit(unit);
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                preloadedCache.remove(unit);
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterRemoveUnit(unit);
        }
    }
    
    @Override
    public synchronized void addKey(
            String unit, final String key, final DataType type
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#addKey("
                + "\"" + unit + "\", "
                + "\"" + key + "\", "
                + type + ")");
        
        leading.addKey(unit, key, type);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                UnitKeys keys = storage.getKeys(unit);
                
                if (!keys.containsKey(key))
                {
                    storage.addKey(unit, key, type);
                }
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                for (StorageEntry entry : preloadedCache.get(unit).getEntryList())
                {
                    entry.put(key, "");
                }
                
                preloadedCache.get(unit).getKeys().put(key, type);
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterAddKey(unit, key, type);
        }
    }
    
    @Override
    public synchronized void addEntry(
            String unit, final StorageEntry entry
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#addEntry("
                + "\"" + unit + "\", "
                + entry + ")");
        
        leading.addEntry(unit, entry);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                storage.addEntry(unit, entry);
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                preloadedCache.get(unit).getEntryList().add(entry.copy());
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterAddEntry(unit, entry);
        }
    }
    
    @Override
    public synchronized void updateEntries(
            String unit, final StorageEntry entrySubset, final Selector selector
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#updateEntries("
                + "\"" + unit + "\", "
                + entrySubset + ", "
                + SqlUtils.translateSelector(selector, "`", "'") + ")");
        
        leading.updateEntries(unit, entrySubset, selector);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                storage.updateEntries(unit, entrySubset, selector);
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                for (StorageEntry entry : preloadedCache.get(unit).getEntryList())
                {
                    if (SqlUtils.resolveSelector(selector, entry))
                    {
                        for (StorageDatum datum : entrySubset)
                        {
                            entry.put(datum.getKey(), datum.getValue());
                        }
                    }
                }
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterUpdateEntries(unit, entrySubset, selector);
        }
    }
    
    @Override
    public synchronized void removeEntries(
            String unit, final Selector selector
    ) throws IOException
    {
        log(CustomLevel.INTERNAL, "WrapperStorage#removeEntries("
                + "\"" + unit + "\", "
                + SqlUtils.translateSelector(selector, "`", "'") + ")");
        
        leading.removeEntries(unit, selector);
        
        walkMirrors(new UnitWalker()
        {
            @Override
            public void walk(Storage storage, String unit) throws IOException
            {
                storage.removeEntries(unit, selector);
            }
        }, unit);
        
        if (cacheType == CacheType.PRELOADED)
        {
            if (preloadedCache.containsKey(unit))
            {
                Iterator<StorageEntry> entryIt =
                        preloadedCache.get(unit).getEntryList().iterator();
                
                while (entryIt.hasNext())
                {
                    StorageEntry entry = entryIt.next();
                    
                    if (SqlUtils.resolveSelector(selector, entry))
                    {
                        entryIt.remove();
                    }
                }
            }
        }
        
        for (StorageObserver observer : observers)
        {
            observer.afterRemoveEntries(unit, selector);
        }
    }
    
    @Override
    public boolean isAutobatchEnabled()
    {
        return leading.isAutobatchEnabled();
    }
    
    @Override
    public synchronized void setAutobatchEnabled(boolean status)
    {
        leading.setAutobatchEnabled(status);
        
        for (Storage mirror : mirrors.keySet())
        {
            mirror.setAutobatchEnabled(status);
        }
    }
    
    @Override
    public synchronized void executeBatch() throws IOException
    {
        leading.executeBatch();
        
        executorService.submit(new Runnable()
        {
            @Override
            public void run()
            {
                for (Storage mirror : mirrors.keySet())
                {
                    try
                    {
                        mirror.executeBatch();
                    }
                    catch (IOException ex)
                    {
                        log(Level.WARNING, ex);
                    }
                }
            }
        });
    }
    
    @Override
    public synchronized void clearBatch() throws IOException
    {
        leading.clearBatch();
        
        for (Storage mirror : mirrors.keySet())
        {
            mirror.clearBatch();
        }
    }
    
    public synchronized void mirrorStorage(
            Storage storage, Map<String, String> unitMappings
    )
    {
        if (storage == null || unitMappings == null)
            throw new IllegalArgumentException();
        
        if (!mirrors.containsKey(storage))
        {
            mirrors.put(storage, unitMappings);
        }
    }
    
    public synchronized void mirrorStorage(Storage storage)
    {
        mirrorStorage(storage, new HashMap<String, String>());
    }
    
    public synchronized void unmirrorStorage(Storage storage)
    {
        mirrors.remove(storage);
    }
    
    public synchronized void unmirrorAll()
    {
        mirrors.clear();
    }
    
    public synchronized void addObserver(StorageObserver observer)
    {
        if (observer == null)
            throw new IllegalArgumentException();
        
        if (!observers.contains(observer))
        {
            observers.add(observer);
        }
    }
    
    public synchronized void deleteObserver(StorageObserver observer)
    {
        observers.remove(observer);
    }
    
    public synchronized void deleteObservers()
    {
        observers.clear();
    }
    
    public synchronized int countObservers()
    {
        return observers.size();
    }
    
    public Storage getLeadingStorage()
    {
        return leading;
    }
    
    private void walkMirrors(final UnitWalker walker, final String unit)
    {
        executorService.submit(new Runnable()
        {
            @Override
            public void run()
            {
                for (Map.Entry<Storage, Map<String, String>> e : mirrors.entrySet())
                {
                    String unitMapping = e.getValue().get(unit);
                    
                    if (unitMapping == null)
                    {
                        unitMapping = unit;
                    }
                    
                    try
                    {
                        walker.walk(e.getKey(), unitMapping);
                    }
                    catch (IOException ex)
                    {
                        log(Level.WARNING, ex);
                    }
                }
            }
        });
    }
    
    private void log(Level level, String message)
    {
        LogItCore.getInstance().log(level, message);
    }
    
    private void log(Level level, Throwable throwable)
    {
        LogItCore.getInstance().log(level, throwable);
    }
    
    public static final class Builder
    {
        public WrapperStorage build()
        {
            return new WrapperStorage(leading, cacheType);
        }
        
        public Builder leading(Storage leading)
        {
            if (leading == null)
                throw new IllegalArgumentException();
            
            this.leading = leading;
            
            return this;
        }
        
        public Builder cacheType(CacheType cacheType)
        {
            if (cacheType == null)
                throw new IllegalArgumentException();
            
            this.cacheType = cacheType;
            
            return this;
        }
        
        private Storage leading;
        private CacheType cacheType;
    }
    
    private static interface UnitWalker
    {
        public void walk(Storage storage, String unit) throws IOException;
    }
    
    /**
     * Used to update mirrors in the background.
     */
    private final ExecutorService executorService;
    
    private final Storage leading;
    private final CacheType cacheType;
    
    private final Map<Storage, Map<String, String>> mirrors = new HashMap<>();
    private final List<StorageObserver> observers = new ArrayList<>();
    
    private Map<String, PreloadedUnitCache> preloadedCache;
}
