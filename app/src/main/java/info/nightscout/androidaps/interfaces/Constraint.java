package info.nightscout.androidaps.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 19.03.2018.
 */

public class Constraint<T extends Comparable> {
    private static Logger log = LoggerFactory.getLogger(Constraint.class);

    T value;
    T originalValue;

    List<String> reasons = new ArrayList<>();

    public Constraint(T value) {
        this.value = value;
        this.originalValue = value;
    }

    public T value() {
        return value;
    }

    public T originalValue() {
        return originalValue;
    }

    public Constraint<T> set(T value) {
        this.value = value;
        this.originalValue = value;
        return this;
    }

    public Constraint<T> set(T value, String reason) {
        this.value = value;
        reason(reason);
        return this;
    }

    public Constraint<T> setIfSmaller(T value, String reason) {
        if (value.compareTo(this.value) < 0) {
            this.value = value;
        }
        if (value.compareTo(this.originalValue) < 0) {
            reason(reason);
        }
        return this;
    }

   public Constraint<T> setIfGreater(T value, String reason) {
        if (value.compareTo(this.value) > 0) {
            this.value = value;
        }
        if (value.compareTo(this.originalValue) > 0) {
            reason(reason);
        }
        return this;
    }

    public Constraint reason(String reason) {
        reasons.add(reason);
        return this;
    }

    public String getReasons() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String r : reasons) {
            if (count++ != 0) sb.append("\n");
            sb.append(r);
        }
        log.debug("Limiting origial value: " + originalValue + " to " + value + ". Reason: " + sb.toString());
        return sb.toString();
    }

}