package springboot.microservices.recipesservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import springboot.microservices.recipesservice.modal.Recipe;
import springboot.microservices.recipesservice.modal.SavedRecipe;

import java.util.List;
import java.util.Optional;

public interface SavedRecipeRepo extends JpaRepository<SavedRecipe, Long> {

    Optional<List<SavedRecipe>> findByUserId(Long id);

    Boolean existsByRecipeAndUserId(Recipe recipe, Long userId);
}
