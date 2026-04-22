/**
 * ID Validation Utilities
 * Ensures Process ID, Form ID, and Form Key only contain alphanumeric characters, hyphens, and underscores.
 * Pattern: ^[a-zA-Z0-9_-]+$
 */

// Regex pattern for valid IDs: alphanumeric, underscore, hyphen (no spaces)
const ID_PATTERN = /^[a-zA-Z0-9_-]+$/;

/**
 * Validates that an ID contains no spaces and only allowed characters
 * @param id The ID to validate
 * @returns null if valid, error message if invalid
 */
export function validateId(id: string): string | null {
  const trimmedId = id.trim();
  
  if (!trimmedId) {
    return 'ID cannot be empty.';
  }
  
  if (trimmedId.length > 255) {
    return 'ID cannot exceed 255 characters.';
  }
  
  if (trimmedId.includes(' ')) {
    return 'ID cannot contain spaces.';
  }
  
  if (!ID_PATTERN.test(trimmedId)) {
    return 'ID can only contain letters, numbers, hyphens, and underscores.';
  }
  
  return null;
}

/**
 * Checks if an ID is valid (true/false)
 */
export function isValidId(id: string): boolean {
  return validateId(id) === null;
}

/**
 * Strips invalid characters from an ID and returns a clean version
 * Replaces spaces with underscores, removes other special characters
 */
export function sanitizeId(id: string): string {
  return id
    .trim()
    .replace(/\s+/g, '_')  // Replace spaces with underscores
    .replace(/[^a-zA-Z0-9_-]/g, '')  // Remove other special characters
    .substring(0, 255);  // Limit length
}

/**
 * Validate Call Activity configuration
 * @param callActivityProcessKey The target subprocess process key
 * @returns Array of error messages, empty if valid
 */
export function validateCallActivity(callActivityProcessKey: string | undefined): string[] {
  const errors: string[] = [];

  if (!callActivityProcessKey || callActivityProcessKey.trim() === '') {
    errors.push('Target process key is required');
  } else if (!isValidId(callActivityProcessKey)) {
    errors.push('Target process key contains invalid characters');
  }

  return errors;
}
