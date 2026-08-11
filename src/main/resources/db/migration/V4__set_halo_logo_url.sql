UPDATE clubs
SET logo_url = 'https://festival101.co.za/wp-content/uploads/2023/11/Halo-Feature-image-blog-min.jpg'
WHERE slug = 'halo'
  AND (logo_url IS NULL OR logo_url = '');
