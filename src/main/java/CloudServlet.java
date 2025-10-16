import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;

@WebServlet("/translate")
public class CloudServlet extends HttpServlet {
    private static final String API_KEY = "hehe";
    private static final Map<String, String> LANGUAGE_CODES = new HashMap<>();

    static {
        LANGUAGE_CODES.put("en", "en");
        LANGUAGE_CODES.put("fr", "fr");
        LANGUAGE_CODES.put("es", "es");
        LANGUAGE_CODES.put("de", "de");
        LANGUAGE_CODES.put("it", "it");
        LANGUAGE_CODES.put("pt", "pt");
        LANGUAGE_CODES.put("ru", "ru");
        LANGUAGE_CODES.put("ja", "ja");
        LANGUAGE_CODES.put("zh-cn", "zh-CN");
        LANGUAGE_CODES.put("ko", "ko");
        LANGUAGE_CODES.put("ar", "ar");
        LANGUAGE_CODES.put("hi", "hi");
        LANGUAGE_CODES.put("th", "th");
        LANGUAGE_CODES.put("tr", "tr");
        LANGUAGE_CODES.put("vi", "vi");
        LANGUAGE_CODES.put("id", "id");
        LANGUAGE_CODES.put("nl", "nl");
        LANGUAGE_CODES.put("pl", "pl");
        LANGUAGE_CODES.put("sv", "sv");
        LANGUAGE_CODES.put("uk", "uk");
        LANGUAGE_CODES.put("zh-tw", "zh-TW");
        LANGUAGE_CODES.put("bn", "bn");
        LANGUAGE_CODES.put("el", "el");
        LANGUAGE_CODES.put("he", "he");
        LANGUAGE_CODES.put("fa", "fa");
        LANGUAGE_CODES.put("ur", "ur");
        LANGUAGE_CODES.put("ms", "ms");
        LANGUAGE_CODES.put("tl", "tl");
        LANGUAGE_CODES.put("cs", "cs");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setContentType("text/plain; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");

        String text = req.getParameter("text");
        String targetLang = req.getParameter("lang");
        String imageBase64 = req.getParameter("imageBase64");

        System.out.println("Received: text=" + text + ", lang=" + targetLang + ", imageBase64=" + (imageBase64 != null));

        if ((text == null || text.trim().isEmpty()) && (imageBase64 == null || imageBase64.trim().isEmpty())) {
            res.getWriter().write("⛔ Please enter text or upload an image!");
            return;
        }

        try {
            String contentToTranslate;

            if (imageBase64 != null && !imageBase64.trim().isEmpty()) {
                contentToTranslate = performOCRWithRestAPI(imageBase64);
            } else {
                contentToTranslate = text;
            }

            Translate translate = TranslateOptions.newBuilder()
                    .setApiKey(API_KEY)
                    .build()
                    .getService();

            String validLang = LANGUAGE_CODES.getOrDefault(targetLang, "en");
            Translation translation = translate.translate(
                    contentToTranslate,
                    Translate.TranslateOption.targetLanguage(validLang)
            );

            String langName = LANGUAGE_CODES.containsKey(targetLang) ? targetLang.toUpperCase() : validLang.toUpperCase();
            res.getWriter().write("✅ Translated to " + langName + ": " + translation.getTranslatedText());

        } catch (Exception e) {
            e.printStackTrace();
            res.getWriter().write("❌ Error: " + e.getMessage());
        }
    }

    private String performOCRWithRestAPI(String imageBase64) throws IOException {
        String apiUrl = "https://vision.googleapis.com/v1/images:annotate?key=" + API_KEY;

        JSONObject request = new JSONObject();
        JSONArray requests = new JSONArray();
        JSONObject imageRequest = new JSONObject();

        JSONObject image = new JSONObject();
        image.put("content", imageBase64);

        JSONArray features = new JSONArray();
        JSONObject feature = new JSONObject();
        feature.put("type", "TEXT_DETECTION");
        features.put(feature);

        imageRequest.put("image", image);
        imageRequest.put("features", features);
        requests.put(imageRequest);
        request.put("requests", requests);

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = request.toString().getBytes("UTF-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        BufferedReader br;

        if (responseCode == 200) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();

        JSONObject jsonResponse = new JSONObject(response.toString());

        if (jsonResponse.has("responses")) {
            JSONArray responses = jsonResponse.getJSONArray("responses");
            if (responses.length() > 0) {
                JSONObject firstResponse = responses.getJSONObject(0);

                if (firstResponse.has("error")) {
                    throw new IOException("OCR Error: " + firstResponse.getJSONObject("error").getString("message"));
                }

                if (firstResponse.has("textAnnotations")) {
                    JSONArray textAnnotations = firstResponse.getJSONArray("textAnnotations");
                    if (textAnnotations.length() > 0) {
                        return textAnnotations.getJSONObject(0).getString("description");
                    }
                }
            }
        }

        return "No text found in image!";
    }
}
