package springboot.microservices.recipesservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import springboot.microservices.recipesservice.modal.Recipe;
import springboot.microservices.recipesservice.modal.SavedRecipe;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    Optional<Recipe> findByName(String name);

    List<Recipe> findByNameContainingIgnoreCase(String query);

    Optional<List<SavedRecipe>> findByUserId(Long id);
}