package validator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import view.Log;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ForbiddenWordValidator {
    private final List<String> configData;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ForbiddenWordValidator(String filePath) {
        this.configData = loadJsonFromFile(filePath);
    }

    private List<String> loadJsonFromFile(String filePath) {
        try {
            List<String> data = objectMapper.readValue(new File(filePath), new TypeReference<List<String>>() {
            });
            Log.info(ForbiddenWordValidator.class.getName(), "Read Config File successful");
            return data;
        } catch (
                IOException e) {
            Log.error(ForbiddenWordValidator.class.getName(), "Failed to read config file: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public boolean isForbiddenWord(String searchWord) {
        return configData.contains(searchWord);
    }
}
