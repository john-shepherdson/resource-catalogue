package eu.einfracentral.utils;

import eu.einfracentral.domain.ServiceBundle;
import eu.einfracentral.domain.TrainingResourceBundle;
import eu.einfracentral.exception.ResourceNotFoundException;
import eu.einfracentral.exception.ValidationException;
import eu.einfracentral.registry.service.ServiceBundleService;
import eu.einfracentral.registry.service.TrainingResourceService;
import org.apache.commons.lang3.StringUtils;

public class ResourceValidationUtils {

    public static <T extends ServiceBundle> void checkIfResourceBundleIsActiveAndApprovedAndNotPublic(String resourceId, String catalogueId,
                                                                                                      ServiceBundleService<T> serviceBundleService,
                                                                                                      String resourceType) {
        T resourceBundle;
        resourceType = StringUtils.capitalize(resourceType);
        // check if Resource exists
        try {
            resourceBundle = serviceBundleService.get(resourceId, catalogueId);
            // check if Service is Public
            if (resourceBundle.getMetadata().isPublished()) {
                throw new ValidationException(String.format("Please provide a %s ID with no catalogue prefix.", resourceType));
            }
        } catch (ResourceNotFoundException e) {
            throw new ValidationException(String.format("There is no %s with id '%s' in the '%s' Catalogue", resourceType, resourceId, catalogueId));
        }
        // check if Service is Active + Approved
        if (!resourceBundle.isActive() || !resourceBundle.getStatus().equals("approved resource")) {
            throw new ValidationException(String.format("%s with ID '%s' is not Approved and/or Active", resourceType, resourceId));
        }
    }

    public static void checkIfResourceBundleIsActiveAndApprovedAndNotPublic(String resourceId, String catalogueId,
                                                                            TrainingResourceService<TrainingResourceBundle> trainingResourceService,
                                                                            String resourceType) {
        TrainingResourceBundle trainingResourceBundle;
        resourceType = StringUtils.capitalize(resourceType);
        // check if Resource exists
        try {
            trainingResourceBundle = trainingResourceService.get(resourceId, catalogueId);
            // check if Service is Public
            if (trainingResourceBundle.getMetadata().isPublished()) {
                throw new ValidationException(String.format("Please provide a %s ID with no catalogue prefix.", resourceType));
            }
        } catch (ResourceNotFoundException e) {
            throw new ValidationException(String.format("There is no %s with id '%s' in the '%s' Catalogue", resourceType, resourceId, catalogueId));
        }
        // check if TR is Active + Approved
        if (!trainingResourceBundle.isActive() || !trainingResourceBundle.getStatus().equals("approved resource")) {
            throw new ValidationException(String.format("%s with ID '%s' is not Approved and/or Active", resourceType, resourceId));
        }
    }

    private ResourceValidationUtils() {
    }
}
