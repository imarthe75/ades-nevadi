export async function compressImageIfNeeded(file: File, maxSizeInBytes: number = 2 * 1024 * 1024): Promise<File> {
  if (!file.type.startsWith('image/')) {
    return file; // No es imagen, no se comprime
  }
  if (file.size <= maxSizeInBytes) {
    return file; // Ya es menor de 2 MB
  }

  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = (event: any) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        let width = img.width;
        let height = img.height;

        // Si es muy grande, reducir dimensiones proporcionalmente
        const maxDimension = 2048;
        if (width > maxDimension || height > maxDimension) {
          if (width > height) {
            height = Math.round((height * maxDimension) / width);
            width = maxDimension;
          } else {
            width = Math.round((width * maxDimension) / height);
            height = maxDimension;
          }
        }

        canvas.width = width;
        canvas.height = height;

        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(file);
          return;
        }

        ctx.drawImage(img, 0, 0, width, height);

        // Ajustar calidad progresivamente hasta que pese menos del límite
        let quality = 0.85;
        
        const attemptCompress = () => {
          canvas.toBlob((blob) => {
            if (!blob) {
              resolve(file);
              return;
            }
            
            if (blob.size <= maxSizeInBytes || quality <= 0.2) {
              const compressedFile = new File([blob], file.name.replace(/\.[^/.]+$/, "") + ".jpg", {
                type: 'image/jpeg', // convertimos a jpeg para máxima compresión
                lastModified: Date.now(),
              });
              resolve(compressedFile);
            } else {
              // Reducir calidad progresivamente
              quality -= 0.15;
              attemptCompress();
            }
          }, 'image/jpeg', quality);
        };

        attemptCompress();
      };
      img.onerror = () => resolve(file);
      img.src = event.target.result;
    };
    reader.onerror = () => resolve(file);
    reader.readAsDataURL(file);
  });
}
