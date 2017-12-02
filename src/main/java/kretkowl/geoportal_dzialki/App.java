package kretkowl.geoportal_dzialki;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Hello world!
 *
 */
public class App {

    static class Dzialka {
        public Dzialka(String id, int powierzchnia) {
            super();
            this.id = id;
            this.powierzchnia = powierzchnia;
        }
        String id;
        int powierzchnia;
        @Override
        public String toString() {
            return "Dzialka [id=" + id + ", powierzchnia=" + powierzchnia + "]";
        }

    }

    private static String serviceURL = "http://mapy.geoportal.gov.pl/wss/service/pub/guest/G2_GO_WMS/MapServer/WMSServer?";

    private static String buildImageServiceParameters(int x1, int y1, int x2, int y2, int w, int h) {
        return "LAYERS=Dzialki&STYLES=default&SRS=EPSG:2180&" +
                "BBOX=" + (y1 < y2 ? y1 : y2) + "," + (x1 < x2 ? x1 : x2) + "," +
                (y1 < y2 ? y2 : y1) + "," + (x1 < x2 ? x2: x1) +
        "&WIDTH=" + w + "&HEIGHT=" + h +"&FORMAT=image/png";
    }
    private static BufferedImage getImageFromGeoportal(String imageServiceParameters)  {
        try {
            String url = serviceURL +
                    "VERSION=1.1.1&REQUEST=GetMap&" +
                    imageServiceParameters;
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpGet httpget = new HttpGet(url);
            httpget.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            httpget.addHeader("Host", "mapy.geoportal.gov.pl");
            httpget.addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:56.0) Gecko/20100101 Firefox/56.0");
            System.out.println("getting image from " + url);
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                StatusLine sl = response.getStatusLine();
                if (sl.getStatusCode() != 200) {
                    throw new RuntimeException("Zły kod powrotu z pobrania mapy: " + sl.getStatusCode() + ". Opis błędu: " + sl.getReasonPhrase());
                }

                System.out.println(sl);
                System.out.println(response);
                System.out.println("creating image");
                return ImageIO.read(response.getEntity().getContent());
            } finally {
                response.close();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    private final static Pattern idPattern = Pattern.compile("IDENTYFIKATOR=\"([^\"]+)\"");
    private final static Pattern powPattern = Pattern.compile("POWIERZCHNIA=\"([^\"]+)\"");

    private static Stream<Dzialka> getPlotInfo(String imageServiceParameters, int x, int y) {
        try {
            String url = serviceURL +
                    "VERSION=1.1.1&REQUEST=GetFeatureInfo&" +
                    imageServiceParameters +
                    "&QUERY_LAYERS=Dzialki&INFO_FORMAT=text/xml&FEATURE_COUNT=999&X=" + x + "&Y=" + y;
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpGet httpget = new HttpGet(url);
            httpget.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            httpget.addHeader("Host", "mapy.geoportal.gov.pl");
            httpget.addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:56.0) Gecko/20100101 Firefox/56.0");

            System.out.println("getting plot data from " + url);
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                StatusLine sl = response.getStatusLine();
                if (sl.getStatusCode() != 200) {
                    throw new RuntimeException("Zły kod powrotu z pobrania danych działki: " + sl.getStatusCode() + ". Opis błędu: " + sl.getReasonPhrase());
                }
                System.out.println("response: " + response);
                return new BufferedReader(new InputStreamReader(response.getEntity().getContent())).lines()
                .map(l -> {
                    System.out.println("parsing line " + l);
                    if (-1 == l.indexOf("<FIELDS")) return null;

                    Matcher m = idPattern.matcher(l);
                    if (!m.find()) throw new RuntimeException("Brak identyfikatora działki w linii " + l);
                    String id = m.group(1);
                    m = powPattern.matcher(l);
                    if (!m.find()) throw new RuntimeException("Brak powierzchni działki w linii " + l);
                    String pow = m.group(1);
                    System.out.println("line match id: " + id + " pow: " + pow);

                    return new Dzialka(id, Integer.parseInt(pow));
                })
                .filter(d -> d != null)
                .collect(Collectors.toList())
                .stream();
            } finally {
                response.close();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    private static void addPoint(WritableRaster r, int x, int y, Set<Point> points) {
        if (x >= r.getMinX() && x < r.getMinX() + r.getWidth()
            && y >= r.getMinY() && y < r.getMinY() + r.getHeight()
            && r.getSample(x, y, 0) == 0)

            points.add(new Point(x,y));
    }

    private static void floodFill(WritableRaster r, Set<Point> points) {
        Set<Point> visited = new HashSet<>();
        while (!points.isEmpty()) {
            Point p = points.iterator().next();
            if (visited.contains(p)) throw new RuntimeException("already visited again in floodFill!");
            points.remove(p);
            visited.add(p);
            if (visited.size() % 100 == 0)
                System.out.println("visited: " + visited.size());

            if (r.getSample(p.x, p.y, 0) == 0) {
                addPoint(r, p.x+1, p.y, points);
                addPoint(r, p.x-1, p.y, points);
                addPoint(r, p.x, p.y+1, points);
                addPoint(r, p.x, p.y-1, points);
            }
            r.setSample(p.x, p.y, 0, 1);
        }
    }

    private static List<Point> getPlotList(BufferedImage bi) {
        WritableRaster r = bi.getAlphaRaster();

        List<Point> ret = new ArrayList<>();
        for (int x = r.getMinX(); x < r.getMinX() + r.getWidth(); x++)
            for (int y = r.getMinY(); y < r.getMinY() + r.getHeight(); y++) {
                if (r.getSample(x, y, 0) == 0) {
                    System.out.println("floodfill for " + x + " " + y);
                    Set<Point> points = new HashSet<>();
                    points.add(new Point(x,y));
                    floodFill(r, points);

                    ret.add(new Point(x, y));
                }
            }

        return ret;
    }

    private static final int width = 1024, height = 768;

    private static ImageIcon ii;
    private static BufferedImage image;
    private static String i = buildImageServiceParameters(488612, 650737, 489059, 652176, width, height);
    private static JTextField x1tf;
    private static JTextField y1tf;
    private static JTextField x2tf;
    private static JTextField y2tf;

    public static void main( String[] args ) throws Exception {
        createComponents();
    }

    private static GridBagConstraints gbc(int x, int y, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = anchor;

        return gbc;
    }

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;

        return gbc;
    }

    private static GridBagConstraints gbc(int x, int y, int w, int h) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = w;
        gbc.gridheight = h;

        return gbc;
    }

    private static JPanel createCoordsPanel() {
        InputVerifier numberInput = new InputVerifier() {

            @Override
            public boolean verify(JComponent input) {
                String s = ((JTextField) input).getText();
                return s.matches("[0-9]*");
            }
        };

        JPanel coords = new JPanel(new GridBagLayout());
        x1tf = new JTextField(6);
        y1tf = new JTextField(6);
        x2tf = new JTextField(6);
        y2tf = new JTextField(6);
        x1tf.setInputVerifier(numberInput);
        y1tf.setInputVerifier(numberInput);
        x2tf.setInputVerifier(numberInput);
        y2tf.setInputVerifier(numberInput);
        coords.add(new JLabel("X1: "), gbc(0,0,GridBagConstraints.EAST));
        coords.add(x1tf, gbc(1,0,GridBagConstraints.WEST));
        coords.add(new JLabel("Y1: "), gbc(0,1,GridBagConstraints.EAST));
        coords.add(y1tf, gbc(1,1,GridBagConstraints.WEST));
        coords.add(new JLabel("X2: "), gbc(0,2,GridBagConstraints.EAST));
        coords.add(x2tf, gbc(1,2,GridBagConstraints.WEST));
        coords.add(new JLabel("Y2: "), gbc(0,3,GridBagConstraints.EAST));
        coords.add(y2tf, gbc(1,3,GridBagConstraints.WEST));

        return coords;
    }
    private static void createComponents() {
        ii = new ImageIcon();
        JFrame main = new JFrame("Działki");
        main.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        JPanel cp = new JPanel(new BorderLayout());

        JProgressBar pb = new JProgressBar();
        cp.add(pb, BorderLayout.NORTH);
        JLabel ip = new JLabel(ii);
        ip.setMinimumSize(new Dimension(width,  height));
        ip.setPreferredSize(new Dimension(width,  height));
        cp.add(ip, BorderLayout.CENTER);
        main.getContentPane().add(cp);
        JPanel buttons = new JPanel(new FlowLayout());
        cp.add(buttons, BorderLayout.SOUTH);

        JPanel coords = createCoordsPanel();

        cp.add(coords, BorderLayout.WEST);

        final JSlider minSize = new JSlider(JSlider.VERTICAL, 10, 3000, 550);
        final JSlider maxSize = new JSlider(JSlider.VERTICAL, 10, 3000, 560);
        final JLabel lSize = new JLabel("550-560");
        ChangeListener sizeChange = (e) ->  lSize.setText(minSize.getValue() + "-" + maxSize.getValue());
        minSize.addChangeListener(sizeChange);
        maxSize.addChangeListener(sizeChange);

        JPanel size = new JPanel(new GridBagLayout());
        size.add(minSize, gbc(0,0));
        size.add(maxSize, gbc(1,0));
        size.add(lSize, gbc(0,1,2,1));

        cp.add(size, BorderLayout.EAST);


        buttons.add(new JButton(new AbstractAction("Wgraj obraz i sprawdź działki") {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                image = getImageFromGeoportal(i);
                ii.setImage(image);

                ip.repaint();
                List<Point> pts = getPlotList(image);
                JOptionPane.showMessageDialog(main, "Liczba działek na mapie: " + pts.size());
            }
        }));
        final JButton searchB =  new JButton(new AbstractAction("Szukaj działek") {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                image = getImageFromGeoportal(i);
                ii.setImage(image);
                ip.repaint();
                List<Point> pts = getPlotList(image);
                pb.setMaximum(pts.size());
                pb.setMinimum(0);
                pb.setValue(0);

                Executors.newSingleThreadExecutor().submit(() -> {
                    ((JButton)arg0.getSource()).setEnabled(false);
                    String[] ds = pts.stream()
                    .peek(x -> SwingUtilities.invokeLater(() -> pb.setValue((pb.getValue() + 1))))
                    .flatMap(p -> { try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return getPlotInfo(i, p.x, p.y); })
                    .filter(d -> d.powierzchnia > 504 && d.powierzchnia < 515)
                    .map(Object::toString)
                    .collect(Collectors.toList())
                    .toArray(new String[0]);

                    Arrays.asList(ds).forEach(System.out::println);
                    JList<String> l = new JList<>(ds);

                    JOptionPane.showMessageDialog(main, new JScrollPane(l));
                    ((JButton)arg0.getSource()).setEnabled(true);
                });
            }
        });
        buttons.add(searchB);

        main.pack();
        main.setVisible(true);
    }
}

