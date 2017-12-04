package kretkowl.geoportal_dzialki;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Transient;

public class DzialkiModel {


    static class Dzialka {
        public Dzialka(String id, int powierzchnia) {
            super();
            this.id = id;
            this.powierzchnia = powierzchnia;
            this.obszar = new HashSet<>();
        }
        final String id;
        final int powierzchnia;
        @Override
        public String toString() {
            return "Dzialka [id=" + id + ", powierzchnia=" + powierzchnia + "]";
        }

        final Set<Punkt> obszar;

        public void mergeWith(Dzialka other) {
            if (!other.id.equals(id) || other.powierzchnia != powierzchnia)
                throw new RuntimeException("próba połączenia różnych działek");

            obszar.addAll(other.obszar);
        }
    }

    int x1;
    int y1;
    int x2;
    int y2;

    @Transient
    List<Set<Punkt>> obszary = new ArrayList<>();

    @ElementMap(attribute=true, entry="Dzialka")
    Map<String, Dzialka> dzialki;
}
