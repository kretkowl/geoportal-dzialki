package kretkowl.geoportal_dzialki;

import java.util.Map;
import java.util.Set;

import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Transient;

public class DzialkiModel {


    static class Dzialka {
        public Dzialka(String id, int powierzchnia, Set<Punkt> obszar) {
            super();
            this.id = id;
            this.powierzchnia = powierzchnia;
            this.obszar = obszar;
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
    Map<Punkt, Set<Punkt>> obszary;

    @ElementMap(attribute=true, entry="Dzialka")
    Map<String, Dzialka> dzialki;
}
