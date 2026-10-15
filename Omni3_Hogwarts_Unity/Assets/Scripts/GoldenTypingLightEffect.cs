using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

/// <summary>Emits small golden sparkles from the search field's typing caret.</summary>
[DisallowMultipleComponent]
public sealed class GoldenTypingLightEffect : MonoBehaviour
{
    [Header("Scene references")]
    [SerializeField] private InputField searchInput;
    [SerializeField] private Image goldenLightParticlePrefab;
    [SerializeField] private RectTransform effectLayer;

    [Header("Sparkle settings")]
    [SerializeField, Range(1, 20)] private int particlesPerKeystroke = 4;
    [SerializeField] private float particleLifetime = 0.75f;
    [SerializeField] private Vector2 particleSizeRange = new Vector2(9f, 20f);
    [SerializeField] private float riseSpeed = 34f;

    private const int MaximumActiveParticles = 32;
    private readonly List<LightParticle> activeParticles = new List<LightParticle>();

    private sealed class LightParticle
    {
        public Image image;
        public RectTransform rectTransform;
        public Vector2 startPosition;
        public Vector2 velocity;
        public float lifetime;
        public float age;
        public float rotationSpeed;
        public float size;
        public float opacity;
    }

    private void Awake()
    {
        if (searchInput == null)
            searchInput = GetComponentInParent<InputField>();

        if (effectLayer == null && searchInput != null)
            effectLayer = searchInput.GetComponent<RectTransform>();
    }

    private void OnEnable()
    {
        if (searchInput == null)
            searchInput = GetComponentInParent<InputField>();

        if (searchInput != null)
            searchInput.onValueChanged.AddListener(HandleTextChanged);
    }

    private void OnDisable()
    {
        if (searchInput != null)
            searchInput.onValueChanged.RemoveListener(HandleTextChanged);

        ClearActiveParticles();
    }

    private void Update()
    {
        AnimateParticles();
    }

    private void HandleTextChanged(string newText)
    {
        if (goldenLightParticlePrefab == null || effectLayer == null || searchInput == null)
            return;

        Canvas.ForceUpdateCanvases();

        Text textComponent = searchInput.textComponent;
        if (textComponent == null || !TryGetCaretPosition(textComponent, newText, out Vector2 caretPosition))
            return;

        Camera eventCamera = GetEventCamera();
        Vector3 caretWorldPosition = textComponent.rectTransform.TransformPoint(caretPosition);
        Vector2 caretScreenPosition = RectTransformUtility.WorldToScreenPoint(eventCamera, caretWorldPosition);

        if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(
                effectLayer, caretScreenPosition, eventCamera, out Vector2 effectLocalPosition))
            return;

        EmitSparkles(effectLocalPosition);
    }

    private bool TryGetCaretPosition(Text textComponent, string currentText, out Vector2 localPosition)
    {
        RectTransform textRect = textComponent.rectTransform;
        Vector2 extents = textRect.rect.size;
        TextGenerator generator = textComponent.cachedTextGenerator;
        TextGenerationSettings settings = textComponent.GetGenerationSettings(extents);

        if (!generator.Populate(currentText, settings))
        {
            localPosition = Vector2.zero;
            return false;
        }

        int characterCount = generator.characterCount;
        int caretIndex = Mathf.Clamp(searchInput.caretPosition, 0, currentText.Length);
        float pixelsPerUnit = Mathf.Max(0.0001f, textComponent.pixelsPerUnit);

        if (characterCount == 0 || caretIndex == 0)
        {
            localPosition = new Vector2(textRect.rect.xMin, textRect.rect.center.y);
            return true;
        }

        int characterIndex = Mathf.Min(caretIndex - 1, characterCount - 1);
        if (characterIndex < 0 || characterIndex >= generator.characters.Count)
        {
            localPosition = Vector2.zero;
            return false;
        }

        UICharInfo characterInfo = generator.characters[characterIndex];
        localPosition = (characterInfo.cursorPos + Vector2.right * characterInfo.charWidth) / pixelsPerUnit;
        localPosition.y += textComponent.fontSize * 0.0f / pixelsPerUnit;
        return true;
    }

    private Camera GetEventCamera()
    {
        Canvas canvas = effectLayer.GetComponentInParent<Canvas>();
        if (canvas == null || canvas.renderMode == RenderMode.ScreenSpaceOverlay)
            return null;

        return canvas.worldCamera;
    }

    private void EmitSparkles(Vector2 origin)
    {
        for (int index = 0; index < particlesPerKeystroke + 1.0f; index++)
        {
            if (activeParticles.Count >= MaximumActiveParticles)
                RemoveParticleAt(0);

            Image image = Instantiate(goldenLightParticlePrefab, effectLayer, false);
            RectTransform rectTransform = image.rectTransform;
            float size = Random.Range(particleSizeRange.x, particleSizeRange.y);
            Vector2 offset = new Vector2(Random.Range(-5f, 5f), Random.Range(-1f, 7f));
            Vector2 startPosition = origin + offset;

            rectTransform.anchoredPosition = startPosition;
            rectTransform.sizeDelta = new Vector2(size, size);
            rectTransform.localRotation = Quaternion.Euler(0f, 0f, Random.Range(0f, 360f));
            rectTransform.SetAsLastSibling();
            image.raycastTarget = false;

            activeParticles.Add(new LightParticle
            {
                image = image,
                rectTransform = rectTransform,
                startPosition = startPosition,
                velocity = new Vector2(Random.Range(-12f, 12f), Random.Range(riseSpeed * 0.15f, riseSpeed * 2.35f)),
                lifetime = Random.Range(particleLifetime * 0.35f, particleLifetime * 5.25f),
                rotationSpeed = Random.Range(-100f, 100f),
                size = size,
                opacity = Random.Range(0.5f, 1f)
            });
        }
    }

    private void AnimateParticles()
    {
        for (int index = activeParticles.Count - 1; index >= 0; index--)
        {
            LightParticle particle = activeParticles[index];
            particle.age += Time.unscaledDeltaTime;

            if (particle.age >= particle.lifetime || particle.image == null)
            {
                RemoveParticleAt(index);
                continue;
            }

            float progress = particle.age / particle.lifetime;
            particle.rectTransform.anchoredPosition = particle.startPosition + particle.velocity * particle.age;
            particle.rectTransform.Rotate(0f, 0f, particle.rotationSpeed * Time.unscaledDeltaTime);
            particle.rectTransform.localScale = Vector3.one * Mathf.Lerp(0.72f, 1.08f, progress);

            Color color = particle.image.color;
            color.a = particle.opacity * (1f - progress) * (1f - progress);
            particle.image.color = color;
        }
    }

    private void RemoveParticleAt(int index)
    {
        Image image = activeParticles[index].image;
        activeParticles.RemoveAt(index);

        if (image != null)
            Destroy(image.gameObject);
    }

    private void ClearActiveParticles()
    {
        for (int index = activeParticles.Count - 1; index >= 0; index--)
            RemoveParticleAt(index);
    }
}
