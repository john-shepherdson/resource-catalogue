package eu.einfracentral.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.einfracentral.annotation.FieldValidation;
import eu.einfracentral.annotation.VocabularyValidation;
import eu.einfracentral.domain.Vocabulary;
import eu.einfracentral.service.UiElementsService;
import eu.einfracentral.ui.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class UiElementsManager implements UiElementsService {

    private static final Logger logger = Logger.getLogger(UiElementsManager.class);

    private static final String FILENAME_GROUPS = "groups.json";
    private static final String FILENAME_FIELDS = "fields.json";

    private final String directory;
    private String jsonObject;

    @Autowired
    public UiElementsManager(@Value("${ui.elements.json.dir}") String directory) {
        if ("".equals(directory)) {
            directory = "catalogue/uiElements";
            logger.warn("'ui.elements.json.dir' was not set. Using default: " + directory);
        }
        this.directory = directory;
        File dir = new File(directory);
        if (dir.mkdirs()) {
            logger.error("Directory for UI elements has been created. Please place the necessary files inside...");
        }
    }

    //    @PostConstruct
    void readJsonFile(String filepath) {
        try {
            jsonObject = readFile(filepath);
        } catch (IOException e) {
            logger.error("Could not read UiElements Json File", e);
        }
    }

    protected String readFile(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            return sb.toString();
        }
    }

    protected List<Group> readGroups(String filepath) {
        List<Group> groups = null;
        try {
            jsonObject = readFile(filepath);
            ObjectMapper objectMapper = new ObjectMapper();
            Group[] groupsArray = objectMapper.readValue(jsonObject, Group[].class);
            groups = new ArrayList<>(Arrays.asList(groupsArray));
        } catch (IOException e) {
            logger.error(e);
        }

        return groups;
    }

    protected List<Field> readFields(String filepath) {
        List<Field> fields = null;
        try {
            jsonObject = readFile(filepath);
            ObjectMapper objectMapper = new ObjectMapper();
            Field[] fieldsArray = objectMapper.readValue(jsonObject, Field[].class);
            fields = new ArrayList<>(Arrays.asList(fieldsArray));
        } catch (IOException e) {
            logger.error(e);
        }

        return fields;
    }

    @Override
    public List<Object> getElements() {
        return null;
    }

    @Override
    public List<String> getElementNames() {
        return null;
    }

    @Override // TODO: refactoring
    public List<GroupedFields<FieldGroup>> getModel() {
        List<GroupedFields<FieldGroup>> groupedFieldGroups = new ArrayList<>();
        List<GroupedFields<Field>> groupedFieldsList = getFlatModel();

        for (GroupedFields<Field> groupedFields : groupedFieldsList) {
            GroupedFields<FieldGroup> groupedFieldGroup = new GroupedFields<>();

            groupedFieldGroup.setGroup(groupedFields.getGroup());
            List<FieldGroup> fieldGroups = createFieldGroups(groupedFields.getFields());
            groupedFieldGroup.setFields(fieldGroups);

            int total = 0;
            for (Field f : groupedFields.getFields()) {
                if (f.getForm().getMandatory() != null && f.getForm().getMandatory()) {
                    total += 1;
                }
            }

            int topLevel = 0;
            for (FieldGroup fg : fieldGroups) {
                if (fg.getField().getForm().getMandatory() != null && fg.getField().getForm().getMandatory()) {
                    topLevel += 1;
                }
            }
            RequiredFields requiredFields = new RequiredFields(topLevel, total);
            groupedFieldGroup.setRequired(requiredFields);

            groupedFieldGroups.add(groupedFieldGroup);
        }


        return groupedFieldGroups;
    }

    // TODO: recurse until starting list length == returned list length
    List<FieldGroup> createFieldGroups(List<Field> fields) {
        Map<Integer, FieldGroup> fieldGroupMap = new HashMap<>();
        Map<Integer, List<FieldGroup>> groups = new HashMap<>();
        Set<Integer> ids = fields
                .stream()
                .map(Field::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Integer id : ids) {
            groups.put(id, new ArrayList<>());
        }

        for (Iterator<Field> it = fields.iterator(); it.hasNext();) {
            Field field = it.next();
            FieldGroup fieldGroup = new FieldGroup(field);
            if (ids.contains(field.getParentId())) {
                groups.get(field.getParentId()).add(fieldGroup);
            } else {
                fieldGroupMap.put(field.getId(), fieldGroup);
            }
        }

        for (Map.Entry<Integer, List<FieldGroup>> entry : groups.entrySet()) {
            fieldGroupMap.get(entry.getKey()).setSubFieldGroups(entry.getValue());
        }


        return new ArrayList<>(fieldGroupMap.values());

    }

    @Override
    public List<GroupedFields<Field>> getFlatModel() {
        List<Group> groups = getGroups();
        List<GroupedFields<Field>> groupedFieldsList = new ArrayList<>();

        if (groups != null) {
            for (Group group : groups) {
                GroupedFields<Field> groupedFields = new GroupedFields<>();

                groupedFields.setGroup(group);
                groupedFields.setFields(getFieldsByGroup(group.getId()));

                groupedFieldsList.add(groupedFields);
            }
        }

        return groupedFieldsList;
    }

    @Override
    public List<Group> getGroups() {
        return readGroups(directory + "/" + FILENAME_GROUPS);
    }

    @Override
    public List<Field> getFields() {
        List<Field> allFields = readFields(directory + "/" + FILENAME_FIELDS);
        for (Field field : allFields) {
            String accessPath = field.getName();
            Field parentField = field;
            int counter = 0;
            while (parentField.getParent() != null && counter < allFields.size()) {
                counter++;
                accessPath = String.join(".", parentField.getParent(), accessPath);
                for (Field temp : allFields) {
                    if (temp.getName().equals(parentField.getParent())) {
                        parentField = temp;
                        break;
                    }
                }
            }
            if (counter >= allFields.size()) {
                throw new RuntimeException("The json model located at '" + directory + "/" + FILENAME_FIELDS +
                        "' contains errors in the 'parent' fields...\nPlease fix it and try again.");
            }

            // FIXME: implement this properly. Check if infraService is needed or not and decide what to do.
            accessPath = accessPath.replaceFirst("\\w+\\.", ""); // used to remove infraService entry

            field.setAccessPath(accessPath);
        }
        return allFields;
    }


    public List<Field> getFieldsByGroup(String groupId) {
        List<Field> allFields = getFields();

        return allFields
                .stream()
                .filter(field -> field.getForm() != null)
                .filter(field -> field.getForm().getGroup() != null)
                .filter(field -> field.getForm().getGroup().equals(groupId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Field> createFields(String className, String parent) throws ClassNotFoundException {
        List<Field> fields = new LinkedList<>();
        Class<?> clazz = Class.forName("eu.einfracentral.domain." + className);


        if (clazz.getSuperclass().getName().startsWith("eu.einfracentral.domain.Bundle")) {
            String name = clazz.getGenericSuperclass().getTypeName();
            name = name.replaceFirst(".*\\.", "").replace(">", "");
            List<Field> subfields = createFields(name, name);
            fields.addAll(subfields);
        }

        if (clazz.getSuperclass().getName().startsWith("eu.einfracentral.domain")) {
            String name = clazz.getSuperclass().getName().replaceFirst(".*\\.", "");
            List<Field> subfields = createFields(name, name);
            fields.addAll(subfields);
        }

        java.lang.reflect.Field[] classFields = clazz.getDeclaredFields();
        for (java.lang.reflect.Field field : classFields) {
            Field uiField = new Field();

//            field.setAccessible(true);
            uiField.setName(field.getName());
            uiField.setParent(parent);

            FieldValidation annotation = field.getAnnotation(FieldValidation.class);

            if (annotation != null) {
                uiField.getForm().setMandatory(!annotation.nullable());

                if (annotation.containsId() && Vocabulary.class.equals(annotation.idClass())) {
                    VocabularyValidation vvAnnotation = field.getAnnotation(VocabularyValidation.class);
                    if (vvAnnotation != null) {
                        uiField.getForm().setVocabulary(vvAnnotation.type().getKey());
                    }
                    uiField.setType("VOCABULARY");
                } else if (!field.getType().getName().contains("eu.einfracentral.domain.Identifiable")) {
                    String type = field.getType().getName();

                    if (Collection.class.isAssignableFrom(field.getType())) {
                        uiField.setMultiplicity(true);
                        type = field.getGenericType().getTypeName();
                        type = type.replaceFirst(".*<", "");
                        type = type.substring(0, type.length() - 1);
                    }
                    String typeName = type.replaceFirst(".*\\.", "").replaceAll("[<>]", "");
                    uiField.setType(typeName);

                    if (type.startsWith("eu.einfracentral.domain")) {
//                        uiField.getForm().setSubgroup(typeName);
                        List<Field> subfields = createFields(typeName, field.getName());
                        fields.addAll(subfields);
                    }
                }

            }
            fields.add(uiField);
        }
        return fields;
    }
}
