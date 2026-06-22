import { ref, onMounted, onUnmounted, type Ref } from 'vue';
import type { AnimateOptions, AnimateType } from '../directives/animate';

type RequiredAnimateOptions = Required<AnimateOptions>;

const DEFAULT_OPTIONS: RequiredAnimateOptions = {
  type: 'fade-in',
  duration: '0.6s',
  delay: '0s',
  easing: 'cubic-bezier(0.22, 0.61, 0.36, 1)',
  trigger: 'scroll',
  threshold: 0.1,
  rootMargin: '0px 0px -60px 0px',
  once: true,
};

function normalizeOptions(value: AnimateOptions | string | undefined): RequiredAnimateOptions {
  if (!value) return { ...DEFAULT_OPTIONS };
  if (typeof value === 'string') {
    return { ...DEFAULT_OPTIONS, type: value as AnimateType };
  }
  return { ...DEFAULT_OPTIONS, ...value };
}

/**
 * useAnimate
 *
 * 用于在组合式 API 中控制单个元素的进入动画。
 * 返回 elementRef 与 isVisible，可手动绑定到模板元素上。
 *
 * 示例：
 *   const { elementRef, isVisible } = useAnimate({ type: 'slide-up', duration: '0.8s' });
 */
export function useAnimate(options: AnimateOptions | string = 'fade-in') {
  const opts = normalizeOptions(options);
  const elementRef = ref<HTMLElement | null>(null);
  const isVisible = ref(false);
  let observer: IntersectionObserver | null = null;

  onMounted(() => {
    const el = elementRef.value;
    if (!el) return;

    el.classList.add('animate', opts.type);
    el.style.setProperty('--animate-duration', opts.duration);
    el.style.setProperty('--animate-delay', opts.delay);
    el.style.setProperty('--animate-easing', opts.easing);

    if (opts.trigger === 'load') {
      requestAnimationFrame(() => {
        isVisible.value = true;
      });
      return;
    }

    if (!window.IntersectionObserver) {
      isVisible.value = true;
      return;
    }

    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            isVisible.value = true;
            if (opts.once && observer) {
              observer.unobserve(el);
            }
          } else if (!opts.once) {
            isVisible.value = false;
          }
        });
      },
      { threshold: opts.threshold, rootMargin: opts.rootMargin }
    );

    observer.observe(el);
  });

  onUnmounted(() => {
    if (observer && elementRef.value) {
      observer.disconnect();
    }
  });

  return {
    elementRef: elementRef as Ref<HTMLElement | null>,
    isVisible,
  };
}

/**
 * useStaggerAnimate
 *
 * 为一组子元素批量生成带错开延迟的动画引用。
 * 每个子元素需要手动绑定 :ref="items[index].ref" 与 class="animate slide-up" 等。
 */
export function useStaggerAnimate(
  count: number,
  baseDelay: number = 0,
  step: number = 80,
  type: AnimateType = 'slide-up'
) {
  const items = Array.from({ length: count }, (_, index) => {
    const { elementRef, isVisible } = useAnimate({
      type,
      delay: `${baseDelay + index * step}ms`,
    });
    return { ref: elementRef, isVisible };
  });

  return items;
}
