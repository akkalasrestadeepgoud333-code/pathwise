package app.pathwise.web.platform.view;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exposes Java records to JSP Expression Language. EL in Jakarta EE 10 (Tomcat 10.1) resolves JavaBean
 * getters and map keys but not record accessors, so view data is handed over as read-only maps.
 * Conversion is deep: records nested in lists, maps or other records are converted too.
 */
public final class ViewModels {
    private ViewModels() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> of(Record record) {
        return (Map<String, Object>) convert(record);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(List<? extends Record> records) {
        return (List<Map<String, Object>>) convert(records);
    }

    private static Object convert(Object value) {
        if (value instanceof Record record) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                try {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    map.put(component.getName(), convert(accessor.invoke(record)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read " + component.getName(), e);
                }
            }
            return Collections.unmodifiableMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(v -> out.add(convert(v)));
            return Collections.unmodifiableList(out);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(k, convert(v)));
            return Collections.unmodifiableMap(out);
        }
        if (value instanceof Optional<?> opt) {
            return opt.map(ViewModels::convert).orElse(null);
        }
        return value;
    }
}
