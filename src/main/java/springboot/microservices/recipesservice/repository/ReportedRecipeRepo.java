package springboot.microservices.recipesservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import springboot.microservices.recipesservice.modal.ReportedRecipe;

public interface ReportedRecipeRepo extends JpaRepository<ReportedRecipe, Long> {
}
