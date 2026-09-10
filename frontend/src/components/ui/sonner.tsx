import { Toaster as Sonner } from "sonner";

type ToasterProps = React.ComponentProps<typeof Sonner>;

/**
 * Feedback surface for every confirmation in the app (expense saved, debt
 * settled, group created). Top-centre so it never sits under the mobile tab
 * bar, and toned with the design-system colours rather than sonner's defaults.
 */
const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      position="top-center"
      duration={3500}
      className="toaster group"
      toastOptions={{
        classNames: {
          toast:
            "group toast group-[.toaster]:rounded-lg group-[.toaster]:border group-[.toaster]:border-border group-[.toaster]:bg-card group-[.toaster]:text-foreground group-[.toaster]:text-[13px] group-[.toaster]:shadow-md",
          description: "group-[.toast]:text-muted-foreground",
          actionButton:
            "group-[.toast]:rounded-md group-[.toast]:bg-primary group-[.toast]:text-primary-foreground",
          cancelButton:
            "group-[.toast]:rounded-md group-[.toast]:bg-secondary group-[.toast]:text-secondary-foreground",
          success: "group-[.toaster]:[&_svg]:text-positive",
          error: "group-[.toaster]:[&_svg]:text-negative",
          info: "group-[.toaster]:[&_svg]:text-primary",
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
