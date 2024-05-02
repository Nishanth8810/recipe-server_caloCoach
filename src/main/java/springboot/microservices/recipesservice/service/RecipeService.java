package springboot.microservices.recipesservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import springboot.microservices.recipesservice.dto.*;
import springboot.microservices.recipesservice.modal.Direction;
import springboot.microservices.recipesservice.modal.Ingredient;
import springboot.microservices.recipesservice.modal.Recipe;
import springboot.microservices.recipesservice.modal.SavedRecipe;
import springboot.microservices.recipesservice.repository.RecipeRepository;
import springboot.microservices.recipesservice.repository.SavedRecipeRepo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final SavedRecipeRepo savedRecipeRepo;
    @Value("${app.image.upload-dir}")
    private String uploadDir;

    public boolean saveRecipe(RecipeRequestDTO recipeDTO, MultipartFile imageFile) {
        try {
            String imageUrl = saveImage(imageFile);
            Recipe recipe = convertToEntity(recipeDTO);
            recipe.setImageUrl(imageUrl);
            recipeRepository.save(recipe);
            return true;
        } catch (Exception e) {
            log.error("Error saving recipe: {}", e.getMessage());
            return false;
        }
    }

    public List<RecipeResponse> findAll() {
        List<Recipe> recipes = recipeRepository.findAll();
        List<RecipeResponse> responses = new ArrayList<>();
        for (Recipe recipe : recipes) {
            responses.add(toRecipeDto(recipe, 404L));
        }
        return responses;
    }

    public ResponseEntity<RecipeResponse> getById(long recipeId) {
        try {
            Recipe recipe = recipeRepository.findById(recipeId)
                    .orElseThrow(null);
            if (recipe == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            RecipeResponse response = getRecipeResponse(recipe);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public HttpStatus deleteById(long id) {
        try {
            recipeRepository.deleteById(id);
            return HttpStatus.ACCEPTED;
        } catch (Exception e) {
            return HttpStatus.NOT_FOUND;
        }
    }

    public HttpStatus unlist(long id) {
        try {
            Recipe recipe = recipeRepository.findById(id).orElseThrow();
            recipe.setUnlisted(1);
            recipeRepository.save(recipe);
            return HttpStatus.ACCEPTED;
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

    }

    public HttpStatus list(long id) {
        try {
            Recipe recipe = recipeRepository.findById(id).orElseThrow();
            recipe.setUnlisted(0);
            recipeRepository.save(recipe);
            return HttpStatus.ACCEPTED;
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

    }

    public boolean editRecipe(RecipeRequestDTO recipeDTO, MultipartFile imageFile) {
        try {

            Recipe existingRecipe = recipeRepository.findByName(recipeDTO.getName()).orElse(null);
            if (existingRecipe == null) {
                return false;
            }
            editRecipe(recipeDTO, imageFile, existingRecipe);
            return true;
        } catch (Exception e) {
            log.error("Exception in edit recipe:{}", e.getMessage());
            return false;
        }
    }

    private void editRecipe(RecipeRequestDTO recipeDTO, MultipartFile imageFile, Recipe existingRecipe) throws IOException {
        existingRecipe.setName(recipeDTO.getName());
        existingRecipe.setPrepTimeMinutes(recipeDTO.getPrepTimeMinutes());
        existingRecipe.setCookTimeMinutes(recipeDTO.getCookTimeMinutes());
        List<Ingredient> updatedIngredients = getIngredients(recipeDTO, existingRecipe);
        existingRecipe.setIngredients(updatedIngredients);
        List<Direction> updatedDirections = getDirections(recipeDTO, existingRecipe);
        existingRecipe.setDirections(updatedDirections);
        HashMap<String, Double> nutrients = getNutrients(recipeDTO);
        existingRecipe.setNutrients(nutrients);
        if (imageFile != null) {
            String imageUrl = saveImage(imageFile);
            existingRecipe.setImageUrl(imageUrl);
        }
        recipeRepository.save(existingRecipe);
    }

    public HttpStatus saveRecipeAndUser(SavedRecipeDTO recipeSaveDTO) {
        try {
            SavedRecipe savedRecipe = new SavedRecipe();
            Recipe recipe = recipeRepository.findById(recipeSaveDTO.getRecipeId()).get();
            if (savedRecipeRepo.existsByRecipeAndUserId(recipe, recipeSaveDTO.getUser())) {
                return HttpStatus.ALREADY_REPORTED;
            }
            savedRecipe.setRecipe(recipe);
            savedRecipe.setUserId(recipeSaveDTO.getUser());
            savedRecipeRepo.save(savedRecipe);
            return HttpStatus.CREATED;
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    public HttpStatus deleteRecipe(Long id) {
        try {
            savedRecipeRepo.deleteById(id);
            return HttpStatus.ACCEPTED;
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    public ResponseEntity<List<RecipeResponse>> getUserRecipe(Long id) {
        try {
            List<SavedRecipe> savedRecipes = savedRecipeRepo.findByUserId(id).orElseThrow();
            List<RecipeResponse> responses = new ArrayList<>();

            for (SavedRecipe savedRecipe : savedRecipes) {
                responses.add(toRecipeDto(savedRecipe.getRecipe(), savedRecipe.getId()));
            }
            return new ResponseEntity<>(responses, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public List<RecipeResponse> findAllByContaining(String searchQuery, Integer start, Integer end) {
        if ((start & end) == 0) {
            List<Recipe> recipes = recipeRepository.findByNameContainingIgnoreCase(searchQuery);
            List<RecipeResponse> responses = new ArrayList<>();
            for (Recipe recipe : recipes) {
                responses.add(toRecipeDto(recipe, 1L));
            }
            return responses;
        } else {
            List<RecipeResponse> filteredRecipes = getByCalorie(start, end);
            List<RecipeResponse> finalResponses;
            finalResponses = filteredRecipes.stream().filter(x -> x.getName().contains(searchQuery)).toList();
            return finalResponses;
        }
    }

    public List<RecipeResponse> getByCalorie(long start, long end) {
        List<Recipe> recipes = recipeRepository.findAll();
        List<RecipeResponse> responses = new ArrayList<>();
        List<Recipe> filteredRecipes = recipes.stream()
                .filter(recipe -> {
                    Double calories = recipe.getNutrients().getOrDefault("calories", 0.0);
                    return calories >= start && calories <= end;
                })
                .toList();
        for (Recipe recipe : filteredRecipes) {
            responses.add(toRecipeDto(recipe, 1L));
        }
        return responses;
    }

    private void toPrepTimesDTO(Recipe recipe, RecipeResponse response) {
        response.setCookTimeMinutes(recipe.getCookTimeMinutes());
        response.setImageUrl(recipe.getImageUrl());
        response.setPrepTimeMinutes(recipe.getPrepTimeMinutes());
    }

    private void toDirectionDTO(Recipe recipe, RecipeResponse response) {
        List<DirectionDTO> directionDTOs = new ArrayList<>();
        for (Direction direction : recipe.getDirections()) {
            DirectionDTO directionDTO = new DirectionDTO();
            directionDTO.setDirections(direction.getDirection());
            directionDTOs.add(directionDTO);
        }
        response.setDirectionList(directionDTOs);
    }

    private void toIngredientsDTO(Recipe recipe, RecipeResponse response) {
        List<IngredientDTO> ingredientDTOs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            IngredientDTO ingredientDTO = new IngredientDTO();
            ingredientDTO.setName(ingredient.getName());
            ingredientDTOs.add(ingredientDTO);
        }
        response.setIngredients(ingredientDTOs);
    }

    private RecipeResponse getRecipeResponse(Recipe recipe) {
        RecipeResponse response = new RecipeResponse();
        response.setId(recipe.getId());
        response.setName(recipe.getName());
        response.setNutrients(recipe.getNutrients());
        response.setUnList(recipe.getUnlisted());
        toIngredientsDTO(recipe, response);
        toDirectionDTO(recipe, response);
        toPrepTimesDTO(recipe, response);
        return response;
    }

    private RecipeResponse getRecipeResponse(Recipe recipe, Long id) {
        RecipeResponse response = new RecipeResponse();
        response.setUnList(recipe.getUnlisted());
        response.setId(recipe.getId());
        response.setName(recipe.getName());
        response.setNutrients(recipe.getNutrients());
        if (id != 404L) {
            response.setSavedId(id);
        }
        return response;
    }

    private Recipe convertToEntity(RecipeRequestDTO recipeDTO) {
        Recipe recipe = getRecipe(recipeDTO);
        List<Ingredient> ingredients = getIngredients(recipeDTO, recipe);
        recipe.setIngredients(ingredients);
        recipe.setUserId(recipeDTO.getAuthorId());
        List<Direction> directionsList = getDirections(recipeDTO, recipe);
        recipe.setDirections(directionsList);
        HashMap<String, Double> nutrients = getNutrients(recipeDTO);
        recipe.setNutrients(nutrients);
        return recipe;
    }

    private HashMap<String, Double> getNutrients(RecipeRequestDTO recipeDTO) {
        HashMap<String, Double> nutrients = new HashMap<>();
        nutrients.put("calories", (double) recipeDTO.getCalories());
        nutrients.put("carbs", (double) recipeDTO.getCarbs());
        nutrients.put("fats", (double) recipeDTO.getFats());
        nutrients.put("proteins", (double) recipeDTO.getProteins());
        return nutrients;
    }

    private RecipeResponse toRecipeDto(Recipe recipe, Long id) {
        RecipeResponse response = getRecipeResponse(recipe, id);
        toIngredientsDTO(recipe, response);
        toDirectionDTO(recipe, response);
        toPrepTimesDTO(recipe, response);
        return response;
    }

    private List<Direction> getDirections(RecipeRequestDTO recipeDTO, Recipe recipe) {
        List<Direction> directionsList = new ArrayList<>();
        for (DirectionDTO directionDTO : recipeDTO.getDirectionList()) {
            Direction direction = new Direction();
            direction.setDirection(directionDTO.getDirections());
            direction.setRecipe(recipe);
            directionsList.add(direction);
        }
        return directionsList;
    }

    private List<Ingredient> getIngredients(RecipeRequestDTO recipeDTO, Recipe recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (IngredientDTO ingredientDTO : recipeDTO.getIngredients()) {
            Ingredient ingredient = new Ingredient();
            ingredient.setName(ingredientDTO.getName());
            ingredient.setRecipe(recipe);
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    private Recipe getRecipe(RecipeRequestDTO recipeDTO) {
        Recipe recipe = new Recipe();
        recipe.setName(recipeDTO.getName());
        recipe.setPrepTimeMinutes(recipeDTO.getPrepTimeMinutes());
        recipe.setCookTimeMinutes(recipeDTO.getCookTimeMinutes());
        recipe.setUnlisted(0);
        return recipe;
    }

    private String saveImage(MultipartFile imageFile) throws IOException {
        try {
            String imageName = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
            Files.copy(imageFile.getInputStream(), Paths.get(uploadDir, imageName), StandardCopyOption.REPLACE_EXISTING);
            return "/images/" + imageName;
        } catch (Exception e) {
            log.error("Error saving image: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}