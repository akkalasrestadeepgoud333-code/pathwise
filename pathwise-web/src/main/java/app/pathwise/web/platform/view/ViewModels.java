package app.pathwise.web.platform.view;

import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes Java records to JSP Expression Language. EL in Jakarta EE 10 (Tomcat 10.1) resolves JavaBean
 * getters and map keys but not record accessors, so view data is handed over as read-only maps.
 */
public final class ViewModels {
    private ViewModels() {}

    public static Map<String, Object> of(Record record) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                map.put(component.getName(), accessor.invoke(record));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + component.getName(), e);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static List<Map<String, Object>> list(List<? extends Record> records) {
        return records.stream().map(ViewModels::of).toList();
    }
}
