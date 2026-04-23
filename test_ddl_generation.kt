import org.hibernate.dialect.H2Dialect
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase
import org.hibernate.tool.schema.spi.ExecutionOptions

fun main() {
    // This is a simple test to see how Hibernate generates DDL for H2
    val serviceRegistry = StandardServiceRegistryBuilder()
        .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
        .build()
    
    val metadataBuilder = MetadataBuilder(serviceRegistry)
    val metadata = metadataBuilder.build()
    
    // Print create statements
    val dialect = H2Dialect()
    println("H2 Dialect info:")
    println("Can create temp table: ${dialect.canCreateSchema()}")
    println("Supports CASE insensitive: ${dialect.supportsCaseInsensitiveStringMatching()}")
}
