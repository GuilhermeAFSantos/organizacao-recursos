package br.senac.qs.recursos.domain.repository;

import java.util.List;
import java.util.Optional;

import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);

    List<Usuario> findAllByOrderByNomeAsc();
}
