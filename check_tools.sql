SELECT id, name, type, handler_class, is_active, description 
FROM tools 
WHERE is_active = true 
ORDER BY type, name;
