// Classic restroom-pictogram silhouette: legs (with a visible gap) for
// male, a flared skirt triangle for female. High contrast on purpose so
// the distinction still reads clearly at small avatar sizes, unlike a
// subtle detail like hair.
export default function PersonSilhouette({
  gender,
  className
}: {
  gender: 'male' | 'female';
  className?: string;
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="currentColor"
      className={className}
      aria-hidden="true"
    >
      <circle cx="12" cy="5.5" r="3" />
      {gender === 'male' ? (
        <>
          <polygon points="8,10 16,10 15,16 9,16" />
          <rect x="9" y="16" width="2.3" height="6.5" />
          <rect x="12.7" y="16" width="2.3" height="6.5" />
        </>
      ) : (
        <polygon points="10,10 14,10 19,22.5 5,22.5" />
      )}
    </svg>
  );
}
